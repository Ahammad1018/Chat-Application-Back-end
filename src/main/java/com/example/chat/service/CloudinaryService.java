package com.example.chat.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(@Value("${cloudinary.cloud-name}") String cloudName,
                             @Value("${cloudinary.api-key}") String apiKey,
                             @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }

    public String uploadFile(MultipartFile file) throws IOException {

        File uploadFile;

        // If it's .mp3 or .mpa, convert to .mp3 File (renaming extension only)
        if (Objects.requireNonNull(file.getOriginalFilename()).endsWith(".mp3") || file.getOriginalFilename().endsWith(".mpa")) {
            uploadFile = convertToMp3File(file); // returns a java.io.File
        } else {
            // For other types, just write to temp file with original name
            uploadFile = File.createTempFile("upload-", getFileExtension(file.getOriginalFilename()));
            file.transferTo(uploadFile);
        }

        Map uploadResult = cloudinary.uploader().upload(uploadFile, ObjectUtils.asMap(
                "resource_type", "auto"
        ));

        System.out.println(uploadResult + " <--- Upload ---");
        return (String) uploadResult.get("url");
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return ".tmp";
        return fileName.substring(fileName.lastIndexOf("."));
    }


    public boolean deleteFile(String publicId, String type) throws Exception {
        Map result = cloudinary.uploader().destroy(extractPublicId(publicId), ObjectUtils.asMap(
                "resource_type", type
        ));

        return "ok".equals(result.get("result"));
    }


    public File convertToMp3File(MultipartFile multipartFile) throws IOException {
        // Get original name (even if wrong)
        String originalName = multipartFile.getOriginalFilename();

        // Ensure extension is .mp3
        String safeFileName; // replace last extension
        if (originalName != null && originalName.endsWith(".mp3")) {
            safeFileName = originalName;
        } else {
            assert originalName != null;
            safeFileName = originalName.replaceAll("\\.[^.]+$", "") + ".mp3";
        }

        // Create a temp file with .mp3 extension
        File mp3File = File.createTempFile(safeFileName.replace(".mp3", ""), ".mp3");
        multipartFile.transferTo(mp3File);

        return mp3File;
    }

    private static String extractPublicId(String urlOrPublicId) {
        // If input looks like a URL, extract publicId, else treat input as publicId directly
        if (urlOrPublicId.startsWith("http")) {
            try {
                // Extract after "/upload/"
                String[] split = urlOrPublicId.split("/upload/");
                if (split.length < 2) return null;

                String path = split[1];

                // Remove version folder (v123456789/)
                if (path.startsWith("v") && path.indexOf("/") > 0) {
                    path = path.substring(path.indexOf("/") + 1);
                }

                // Remove extension
                int dotIndex = path.lastIndexOf('.');
                if (dotIndex > 0) {
                    path = path.substring(0, dotIndex);
                }

                return path;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            // Assume input is publicId already
            return urlOrPublicId;
        }
    }

}
