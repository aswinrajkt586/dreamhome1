package com.dreamhome.engineer;

import com.dreamhome.config.Constants;
import com.dreamhome.config.CookieHelper;
import com.dreamhome.config.PasswordEncoder;
import com.dreamhome.exception.CustomBadRequestException;
import com.dreamhome.exception.CustomUnauthorizedException;
import com.dreamhome.repository.ProjectImageRepository;
import com.dreamhome.repository.ProjectRepository;
import com.dreamhome.repository.UserRepository;
import com.dreamhome.request.*;
import com.dreamhome.table.Project;
import com.dreamhome.table.ProjectImage;
import com.dreamhome.table.Users;
import com.dreamhome.table.enumeration.ApproveReject;
import com.dreamhome.table.enumeration.ImageType;
import com.dreamhome.table.enumeration.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(Constants.API_V1_ENGINEER)
public class EngineerController {

    private static final Logger log = LoggerFactory.getLogger(EngineerController.class);
    final UserRepository userRepository;
    final CookieHelper cookieHelper;
    final ProjectRepository projectRepository;
    final ProjectImageRepository projectImageRepository;
    final ResourceLoader resourceLoader;
    private static final String PLAN_UPLOAD_LOCATION = "static" + File.separator + "plan";
    private static final String MODEL_UPLOAD_LOCATION = "static" + File.separator + "model";

    @PostMapping("/signup")
    public Success signup(@RequestBody EngineerSignup engineerSignup) throws CustomBadRequestException {

        boolean isEmailExists = userRepository.existsByEmailAndRole(engineerSignup.getEmail(), Role.ENGINEER);
        boolean isPhoneExists = userRepository.existsByPhoneAndRole(engineerSignup.getPhone(), Role.ENGINEER);
        if (isEmailExists || isPhoneExists) throw new CustomBadRequestException("Email or phone exists.");

        userRepository.save(
                new Users(engineerSignup.getRole(), false, ApproveReject.PENDING, engineerSignup.getYearOfExperience(),
                        engineerSignup.getJobTitle(), engineerSignup.getPhone(), PasswordEncoder.encodePassword(engineerSignup.getPassword()),
                        engineerSignup.getEmail(), engineerSignup.getName())
        );
        return new Success("Signup successful");
    }

    @PostMapping("/login")
    public Success login(@RequestBody Login login, HttpServletResponse response) throws CustomBadRequestException {

        Users user = userRepository.findByEmailAndRole(login.getEmail(), Role.ENGINEER);
        if (user == null || user.getStatus() != ApproveReject.APPROVED)
            throw new CustomBadRequestException("Bad Credentials.");
        if (!PasswordEncoder.isPasswordMatch(login.getPassword(), user.getPassword()))
            throw new CustomBadRequestException("Bad Credentials.");

        cookieHelper.setCookie(response, user, Constants.ENGINEER_COOKIE_NAME);
        return new Success("Successfully logged in.");
    }

    @GetMapping("/logout")
    public Success logout(HttpServletRequest request, HttpServletResponse response) {
        cookieHelper.deleteCookie(request, response, Constants.ENGINEER_COOKIE_NAME);
        return new Success("Successfully logged out.");
    }

    @GetMapping("/profile")
    public Users getProfile(HttpServletRequest request) throws CustomUnauthorizedException {
        return sessionCheck(request);
    }

    @PostMapping("/active/{val}")
    public Success active(@PathVariable("val") boolean value, HttpServletRequest request) throws CustomUnauthorizedException {
        Users user =  sessionCheck(request);
        user.setActive(value);
        userRepository.save(user);
        return new Success("Active successful");
    }

    @GetMapping("/jobs")
    public List<Project> listAllAssignedWorks(HttpServletRequest request) throws CustomUnauthorizedException {
        Users user = sessionCheck(request);
        return projectRepository.findAllByEngineerId(user.getId());
    }

    @PutMapping("/project")
    public Success updatePlan(@RequestBody UpdateProject updateProject, HttpServletRequest request)
            throws CustomUnauthorizedException, CustomBadRequestException {

        Users user = sessionCheck(request);
        Project project = projectRepository.findByIdAndEngineerId(updateProject.getId(), user.getId());
        if (project == null) throw new CustomBadRequestException("Project not found.");

        project.setThreeDModelAmount(updateProject.getThreeDModelAmount());
        project.setThreeDModelEstimation(updateProject.getThreeDModelEstimation());
        project.setThreeDModelEstimationSubmitted(true);
        project.setPlanEstimation(updateProject.getPlanEstimation());
        project.setPlanAmount(updateProject.getPlanAmount());
        project.setPlanEstimationSubmitted(true);
        projectRepository.save(project);
        return new Success("Plan updated successfully.");
    }

    @GetMapping("/download/plan/{imageId}")
    public ResponseEntity<?> downloadPlanImage(@PathVariable("imageId") UUID imageId,HttpServletRequest request) throws IOException {

        sessionCheck(request);
        ProjectImage projectImage = projectImageRepository.findByImageIdAndType(imageId, ImageType.PLAN);
        if (projectImage == null) throw new CustomBadRequestException("Image not found.");

        try {
            String imageName = imageId + ".jpg";
            Path imagePath = Paths.get(PLAN_UPLOAD_LOCATION).resolve(imageName);
            Resource resource = new UrlResource(imagePath.toUri());
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + imageName);
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            return ResponseEntity.ok().headers(headers).body(resource);
        } catch (Exception e) {
            throw new CustomBadRequestException(e.getMessage());
        }
    }
    @GetMapping("/download/model/{imageId}")
    public ResponseEntity<?> downloadModelImage(@PathVariable("imageId") UUID imageId,HttpServletRequest request) throws IOException {

        sessionCheck(request);
        ProjectImage projectImage = projectImageRepository.findByImageIdAndType(imageId, ImageType.MODEL);
        if (projectImage == null) throw new CustomBadRequestException("Image not found.");

        try {
            String imageName = imageId + ".jpg";
            Path imagePath = Paths.get(MODEL_UPLOAD_LOCATION).resolve(imageName);
            Resource resource = new UrlResource(imagePath.toUri());
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + imageName);
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            return ResponseEntity.ok().headers(headers).body(resource);
        } catch (Exception e) {
            throw new CustomBadRequestException(e.getMessage());
        }
    }

    @PutMapping("/model/project")
    public Success updateModel(@RequestBody UpdateProject updateProject, HttpServletRequest request)
            throws CustomUnauthorizedException, CustomBadRequestException {

        Users user = sessionCheck(request);
        Project project = projectRepository.findByIdAndEngineerId(updateProject.getId(), user.getId());
        if (project == null) throw new CustomBadRequestException("Project not found.");

        project.setThreeDModelAmount(updateProject.getThreeDModelAmount());
        project.setThreeDModelEstimation(updateProject.getThreeDModelEstimation());
        project.setThreeDModelEstimationSubmitted(true);
        projectRepository.save(project);
        return new Success("Model updated successfully.");
    }

    @PostMapping("/plan/{id}")
    public Success uploadPlan(@PathVariable("id") UUID id,
                              @RequestPart("file") MultipartFile file,
                              HttpServletRequest request) throws CustomBadRequestException, CustomUnauthorizedException {

        Users user = sessionCheck(request);
        if (file.isEmpty()) throw new CustomBadRequestException("File is empty.");
        String extension = getFileExtension(file);

        Project project = projectRepository.findByIdAndEngineerId(id, user.getId());
        if (project == null) throw new CustomBadRequestException("Project not found.");

        if (!project.isPlanAmountPaid())
            throw new CustomBadRequestException("Plan amount not paid.");

        try {
            File directory = new File(PLAN_UPLOAD_LOCATION);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            UUID name = UUID.randomUUID();
            String fileName = name + ".jpg";
            File targetFile = new File(directory, fileName);
            FileCopyUtils.copy(file.getInputStream(), new FileOutputStream(targetFile));
            projectImageRepository.save(new ProjectImage(
                    user.getId(), project.getClientId(), project.getId(), name, ImageType.PLAN,extension,
                    project.getName(),user.getName()
            ));
            return new Success("Upload success.");
        } catch (IOException e) {
            throw new CustomBadRequestException(e.getMessage());
        }
    }

    @PostMapping("/model/{id}")
    public Success uploadModel(@PathVariable("id") UUID id,
                               @RequestPart("file") MultipartFile file,
                               HttpServletRequest request) throws CustomBadRequestException, CustomUnauthorizedException {

        Users user = sessionCheck(request);
        if (file.isEmpty()) throw new CustomBadRequestException("File is empty.");
        String extension = getFileExtension(file);

        Project project = projectRepository.findByIdAndEngineerId(id, user.getId());
        if (project == null) throw new CustomBadRequestException("Project not found.");

        if (!project.isThreeDModelAmountPaid())
            throw new CustomBadRequestException("3D Model amount not paid.");

        try {
            File directory = new File(MODEL_UPLOAD_LOCATION);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            UUID name = UUID.randomUUID();
            String fileName = name + ".jpg";
            File targetFile = new File(directory, fileName);
            FileCopyUtils.copy(file.getInputStream(), new FileOutputStream(targetFile));
            projectImageRepository.save(new ProjectImage(
                    user.getId(), project.getClientId(), project.getId(), name, ImageType.MODEL,extension,
                    project.getName(),user.getName()
            ));
            return new Success("Upload success.");
        } catch (IOException e) {
            throw new CustomBadRequestException(e.getMessage());
        }
    }

    @DeleteMapping("/plan/{imageId}")
    public Success deletePlanImage(@PathVariable("imageId") UUID imageId, HttpServletRequest request) throws Exception {
        sessionCheck(request);
        ProjectImage projectImage = projectImageRepository.findByImageIdAndType(imageId, ImageType.PLAN);
        if (projectImage == null) throw new CustomBadRequestException("Image not found.");

        try {
            File imageFile = new File(PLAN_UPLOAD_LOCATION, imageId + ".jpg");
            imageFile.delete();
            projectImageRepository.delete(projectImage);
            return new Success("Delete success.");
        } catch (Exception e) {
            throw new CustomBadRequestException(e.getMessage());
        }
    }

    @DeleteMapping("/model/{imageId}")
    public Success deleteModelImage(@PathVariable("imageId") UUID imageId, HttpServletRequest request) throws Exception {
        sessionCheck(request);
        ProjectImage projectImage = projectImageRepository.findByImageIdAndType(imageId, ImageType.MODEL);
        if (projectImage == null) throw new CustomBadRequestException("Image not found.");

        try {
            File imageFile = new File(MODEL_UPLOAD_LOCATION, imageId + ".jpg");
            imageFile.delete();
            projectImageRepository.delete(projectImage);
            return new Success("Delete success.");
        } catch (Exception e) {
            throw new CustomBadRequestException(e.getMessage());
        }
    }

    @GetMapping("/plan/image/all")
    public List<ProjectImage> getAllPlanImage(HttpServletRequest request) throws CustomUnauthorizedException {
        Users user = sessionCheck(request);
        return projectImageRepository.findAllByEngineerIdAndType(user.getId(), ImageType.PLAN);
    }

    @GetMapping("/model/image/all")
    public List<ProjectImage> getAllModelImage(HttpServletRequest request) throws CustomUnauthorizedException {
        Users user = sessionCheck(request);
        return projectImageRepository.findAllByEngineerIdAndType(user.getId(), ImageType.MODEL);
    }


//    @GetMapping("/plan/images/{projectId}")
//    public List<ProjectImage> getProjectPlanImages(@PathVariable("projectId") UUID projectId,
//                                                   HttpServletRequest request) throws CustomBadRequestException, CustomUnauthorizedException {
//
//        Users user = sessionCheck(request);
//        Project project = projectRepository.findByIdAndClientId(projectId,user.getId());
//        if (project == null) throw new CustomBadRequestException("Project not found.");
//        return projectImageRepository.findAllByProjectIdAndEngineerIdAndType(projectId,user.getId(), ImageType.PLAN);
//    }
//
//    @GetMapping("/model/images/{projectId}")
//    public List<ProjectImage> getProjectModelImages(@PathVariable("projectId") UUID projectId,
//                                                    HttpServletRequest request) throws CustomBadRequestException, CustomUnauthorizedException {
//
//        Users user = sessionCheck(request);
//        Project project = projectRepository.findByIdAndClientId(projectId,user.getId());
//        if (project == null) throw new CustomBadRequestException("Project not found.");
//        return projectImageRepository.findAllByProjectIdAndEngineerIdAndType(projectId,user.getId(), ImageType.MODEL);
//    }

    private Users sessionCheck(HttpServletRequest request) throws CustomUnauthorizedException {
        String cookieId = cookieHelper.getCookieValue(request, Constants.ENGINEER_COOKIE_NAME);
        Users users = userRepository.findByCookie(UUID.fromString(cookieId));
        if (users != null && users.getStatus() == ApproveReject.APPROVED) return users;
        else throw new CustomUnauthorizedException("Unauthorized.");
    }

    public String getFileExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }
        return "";
    }
}
