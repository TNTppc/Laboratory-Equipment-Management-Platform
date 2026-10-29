package cn.labmanage.repair;

import cn.labmanage.common.ApiResult;
import cn.labmanage.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class RepairUploadController {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private final Path uploadDirectory;

    public RepairUploadController(@Value("${app.upload-dir:uploads}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @PostMapping(value = "/repair-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<?> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) throw new BusinessException("请选择图片文件");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) throw new BusinessException("仅支持 JPG、PNG、WEBP 或 GIF 图片");
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".gif";
        };
        String filename = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadDirectory);
            Files.copy(file.getInputStream(), uploadDirectory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("图片保存失败，请稍后重试");
        }
        return ApiResult.ok(Map.of("url", "/uploads/" + filename));
    }
}
