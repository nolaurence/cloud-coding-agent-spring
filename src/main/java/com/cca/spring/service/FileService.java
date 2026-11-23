package com.cca.spring.service;

import com.cca.spring.domain.Workspace;
import com.cca.spring.web.dto.ApiDtos.FileContentResponse;
import com.cca.spring.web.dto.ApiDtos.FileEntryResponse;
import com.cca.spring.web.exception.BadRequestException;
import com.cca.spring.web.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FileService {

  private static final long MAX_TEXT_BYTES = 1024 * 1024;

  public List<FileEntryResponse> list(Workspace workspace, String relativePath) {
    Path directory = resolve(workspace, relativePath);
    if (!Files.isDirectory(directory)) {
      throw new NotFoundException("目录不存在: " + relativePath);
    }
    List<FileEntryResponse> entries = new ArrayList<>();
    try (Stream<Path> stream = Files.list(directory)) {
      stream.filter(path -> !path.getFileName().toString().equals(".git"))
          .sorted(Comparator
              .comparing((Path path) -> !Files.isDirectory(path))
              .thenComparing(path -> path.getFileName().toString().toLowerCase()))
          .forEach(path -> entries.add(toEntry(workspace, path)));
    } catch (IOException ex) {
      throw new BadRequestException("无法读取目录: " + ex.getMessage());
    }
    return entries;
  }

  public FileContentResponse read(Workspace workspace, String relativePath) {
    Path file = resolve(workspace, relativePath);
    if (!Files.isRegularFile(file)) {
      throw new NotFoundException("文件不存在: " + relativePath);
    }
    try {
      long size = Files.size(file);
      if (size > MAX_TEXT_BYTES) {
        throw new BadRequestException("文件过大，仅支持预览 1MB 以内的文本文件");
      }
      byte[] bytes = Files.readAllBytes(file);
      if (isBinary(bytes)) {
        throw new BadRequestException("二进制文件暂不支持预览");
      }
      String content = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
      Instant modifiedAt = Files.getLastModifiedTime(file).toInstant();
      return new FileContentResponse(
          relativePath == null || relativePath.isBlank() ? file.getFileName().toString() : relativePath,
          content,
          size,
          modifiedAt,
          size + "-" + modifiedAt.toEpochMilli());
    } catch (CharacterCodingException ex) {
      throw new BadRequestException("文件不是 UTF-8 文本，暂不支持预览");
    } catch (IOException ex) {
      throw new BadRequestException("无法读取文件: " + ex.getMessage());
    }
  }

  public FileContentResponse write(Workspace workspace, String relativePath, String content) {
    Path file = resolve(workspace, relativePath);
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      return read(workspace, relativePath);
    } catch (IOException ex) {
      throw new BadRequestException("无法写入文件: " + ex.getMessage());
    }
  }

  private Path resolve(Workspace workspace, String relativePath) {
    Path root = Paths.get(workspace.getPath()).toAbsolutePath().normalize();
    Path candidate = root;
    if (StringUtils.hasText(relativePath)) {
      candidate = root.resolve(relativePath).toAbsolutePath().normalize();
    }
    if (!candidate.startsWith(root)) {
      throw new BadRequestException("路径越界");
    }
    return candidate;
  }

  private FileEntryResponse toEntry(Workspace workspace, Path path) {
    try {
      String relative = Paths.get(workspace.getPath()).toAbsolutePath().normalize().relativize(path).toString();
      return new FileEntryResponse(
          path.getFileName().toString(),
          relative.replace('\\', '/'),
          Files.isDirectory(path) ? "directory" : "file",
          Files.isRegularFile(path) ? Files.size(path) : null,
          Files.getLastModifiedTime(path).toInstant());
    } catch (IOException ex) {
      throw new BadRequestException("无法读取文件信息: " + ex.getMessage());
    }
  }

  private boolean isBinary(byte[] bytes) {
    int limit = Math.min(bytes.length, 8192);
    for (int i = 0; i < limit; i++) {
      if (bytes[i] == 0) {
        return true;
      }
    }
    return false;
  }
}
