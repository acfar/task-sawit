package com.interview.sawitpro.controller;

import com.google.api.services.drive.model.File;
import com.interview.sawitpro.service.FileManagerService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class FileController {
    private final FileManagerService fileManagerService;

    public FileController(FileManagerService fileManagerService) {
        this.fileManagerService = fileManagerService;
    }

    @PostMapping(value = "/upload",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE} )
    public ResponseEntity<String> uploadSingleFile(@RequestBody MultipartFile[] files, @RequestParam(required = false) String path) {
        int filesize = files.length;
        AtomicReference<String> fileId = new AtomicReference<>("");
        AtomicReference<String> fileName = new AtomicReference<>("");
        Arrays.asList(files).forEach(
                file->{
                    fileId.set(fileManagerService.uploadFile(file, path));
                    fileName.set(file.getOriginalFilename());
                }
        );

        if (filesize > 1){
            return ResponseEntity.ok("files uploaded successfully");
        }
        return ResponseEntity.ok(fileName + ", uploaded successfully");
    }

    @GetMapping({"/"})
    public ResponseEntity<List<File>> listEverything() throws IOException, GeneralSecurityException {
        List<File> files = fileManagerService.listEverything();
        return ResponseEntity.ok(files);
    }
}
