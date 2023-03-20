package com.interview.sawitpro.service;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import lombok.extern.log4j.Log4j2;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.List;

import com.spire.pdf.PdfDocument;
import com.spire.pdf.PdfPageBase;
import com.spire.pdf.graphics.PdfRGBColor;
import com.spire.pdf.graphics.PdfSolidBrush;
import com.spire.pdf.graphics.PdfTrueTypeFont;

@Service
@Log4j2
public class FileManagerService {
    private final GoogleDriveService googleDriveService;

    public FileManagerService(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    public void downloadFile(String id, OutputStream outputStream) throws IOException, GeneralSecurityException {
        if (id != null) {
            googleDriveService.getInstance().files().get(id).executeMediaAndDownloadTo(outputStream);
        }
    }

    public String uploadFile(MultipartFile file, String filePath) {
        try {
            String folderId = getFolderId(filePath);
            if (null != file) {
                //upload file to gdrive
                File fileMetadata = new File();
                fileMetadata.setParents(Collections.singletonList(folderId));
                fileMetadata.setName(file.getOriginalFilename());
                File uploadFile = googleDriveService.getInstance()
                        .files()
                        .create(fileMetadata, new InputStreamContent(
                                file.getContentType(),
                                new ByteArrayInputStream(file.getBytes()))
                        )
                        .setFields("id").execute();
                //extract word
                java.io.File tmpFile = new java.io.File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
                file.transferTo(tmpFile);
                java.io.File finalImage = GrayScale(tmpFile);
                Map<String, String> textData = getImgText(finalImage);
                if (!textData.get("eng").equals("")){
                    buildEngFile(textData.get("eng"), file.getOriginalFilename());
                }
                if (!textData.get("ch").equals("")){
                    buildChFile(textData.get("ch"), file.getOriginalFilename());
                }
                return uploadFile.getId();
            } else {
                return "Select file";
            }
        } catch (Exception e) {
            log.error("Error: ", e);
        }
        return null;
    }

    public java.io.File GrayScale(java.io.File input) throws IOException {
        BufferedImage image = ImageIO.read(input);

        BufferedImage result = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_RGB);

        Graphics2D graphic = result.createGraphics();
        graphic.drawImage(image, 0, 0, Color.WHITE, null);

        for (int i = 0; i < result.getHeight(); i++) {
            for (int j = 0; j < result.getWidth(); j++) {
                Color c = new Color(result.getRGB(j, i));
                int red = (int) (c.getRed() * 0.299);
                int green = (int) (c.getGreen() * 0.587);
                int blue = (int) (c.getBlue() * 0.114);
                Color newColor = new Color(
                        red + green + blue,
                        red + green + blue,
                        red + green + blue);
                result.setRGB(j, i, newColor.getRGB());
            }
        }
        String name = input.getName().split("\\W")[0];
        String extension = input.getName().split("\\W")[1];
        String fullName = System.getProperty("java.io.tmpdir") + "/" + name + "new." + extension;
        java.io.File output = new java.io.File(fullName);
        ImageIO.write(result, extension, output);
        return output;
    }

    public Map<String, String> getImgText(java.io.File file) {
        Tesseract instance = new Tesseract();
        String imgText, engText = "", chText = "";
        Map<String, String> result = new HashMap<>();
        try {
            if (!file.getName().contains("ImageWithWords4")) {
                instance.setDatapath("./tessdata/");
                imgText = instance.doOCR(file);
            } else {
                instance.setDatapath("./tessdata/");
                instance.setLanguage("chi_sim");
                imgText = instance.doOCR(file);
            }
            imgText = imgText.replaceAll("[!\"#$%&'()*+,-./:;<=>?@\\[\\]^_`{|}~¥€“0-9\n]", "");
            String[] textArr = imgText.split(" ");
            for (String str : textArr) {
                if (!file.getName().contains("ImageWithWords4")){
                    engText = engText.concat(" ").concat(str);
                }else {
                    if (str.matches("^[a-zA-Z]*$")) {
                        engText = engText.concat(" ").concat(str);
                    } else {
                        chText = chText.concat(" ").concat(str);
                    }
                }
            }
            result.put("eng", engText);
            result.put("ch", chText);
            return result;
        } catch (TesseractException e) {
            e.getMessage();
            return new HashMap<>();
        }
    }

    public void buildEngFile(String eng, String fileName) {
        String strWithO = "";
        String strWithoutO = "";
        PdfDocument doc = new PdfDocument();
        PdfPageBase page = doc.getPages().add();
        float y = 30;
        PdfRGBColor rgb1 = new PdfRGBColor(Color.blue);
        PdfSolidBrush brush1 = new PdfSolidBrush(rgb1);
        PdfRGBColor rgb2 = new PdfRGBColor(Color.black);
        PdfSolidBrush brush2 = new PdfSolidBrush(rgb2);
        Font font = new Font("Arial", java.awt.Font.BOLD, 12);
        PdfTrueTypeFont trueTypeFont = new PdfTrueTypeFont(font);
        String[] strArr = eng.split(" ");
        for (String str : strArr){
            if (str.contains("o")|| str.contains("O")){
                strWithO = strWithO.concat(" ").concat(str);
            }else{
                strWithoutO = strWithoutO.concat(" ").concat(str);
            }
        }
        page.getCanvas().drawString(strWithO, trueTypeFont, brush1, 0, (y = y + 30f));
        page.getCanvas().drawString(strWithoutO, trueTypeFont, brush2, 0, (y = y + 30f));
        doc.saveToFile("./output" + "/"+"eng_"+fileName+".pdf");
    }

    public void buildChFile(String ch, String fileName) throws FileNotFoundException {
        java.io.File chFile = new java.io.File("./output" + "/"+"ch_"+fileName+".txt");
        try (PrintWriter out = new PrintWriter("./output" + "/"+"ch_"+fileName+".txt")) {
            out.println(ch);
        }
    }


    public String getFolderId(String path) throws Exception {
        String parentId = null;
        String[] folderNames = path.split("/");

        Drive driveInstance = googleDriveService.getInstance();
        for (String name : folderNames) {
            parentId = findOrCreateFolder(parentId, name, driveInstance);
        }
        return parentId;
    }

    private String findOrCreateFolder(String parentId, String folderName, Drive driveInstance) throws Exception {
        String folderId = searchFolderId(parentId, folderName, driveInstance);
        // Folder already exists, so return id
        if (folderId != null) {
            return folderId;
        }
        //Folder dont exists, create it and return folderId
        File fileMetadata = new File();
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setName(folderName);

        if (parentId != null) {
            fileMetadata.setParents(Collections.singletonList(parentId));
        }
        return driveInstance.files().create(fileMetadata)
                .setFields("id")
                .execute()
                .getId();
    }

    private String searchFolderId(String parentId, String folderName, Drive service) throws Exception {
        String folderId = null;
        String pageToken = null;
        FileList result = null;

        File fileMetadata = new File();
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setName(folderName);

        do {
            String query = " mimeType = 'application/vnd.google-apps.folder' ";
            if (parentId == null) {
                query = query + " and 'root' in parents";
            } else {
                query = query + " and '" + parentId + "' in parents";
            }
            result = service.files().list().setQ(query)
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(pageToken)
                    .execute();

            for (File file : result.getFiles()) {
                if (file.getName().equalsIgnoreCase(folderName)) {
                    folderId = file.getId();
                }
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null && folderId == null);

        return folderId;
    }

    public List<File> listEverything() throws IOException, GeneralSecurityException {
        // Print the names and IDs for up to 10 files.
        FileList result = googleDriveService.getInstance().files().list()
                .setPageSize(10)
                .setFields("nextPageToken, files(id, name)")
                .execute();
        return result.getFiles();
    }


}
