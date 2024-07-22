package com.dreamhome.admin;


import com.dreamhome.config.Constants;
import com.dreamhome.config.CookieHelper;
import com.dreamhome.config.PasswordEncoder;
import com.dreamhome.exception.CustomBadRequestException;
import com.dreamhome.exception.CustomUnauthorizedException;
import com.dreamhome.repository.ProjectRepository;
import com.dreamhome.repository.UserRepository;
import com.dreamhome.request.ApproveReject;
import com.dreamhome.request.Assign;
import com.dreamhome.request.Login;
import com.dreamhome.request.Success;
import com.dreamhome.table.Project;
import com.dreamhome.table.Users;
import com.dreamhome.table.enumeration.ProjectStatus;
import com.dreamhome.table.enumeration.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(Constants.API_V1_ADMIN)
public class AdminController {

    final UserRepository userRepository;
    final CookieHelper cookieHelper;
    private final ProjectRepository projectRepository;

    @PostMapping("/login")
    public Success login(@RequestBody Login login, HttpServletResponse response) throws CustomBadRequestException {

        Users user = userRepository.findByEmailAndRole(login.getEmail(), Role.ADMIN);
        if (user == null) throw new CustomBadRequestException("Bad Credentials.");
        if (!PasswordEncoder.isPasswordMatch(login.getPassword(),user.getPassword())) throw new CustomBadRequestException("Bad Credentials.");

        cookieHelper.setCookie(response,user,Constants.ADMIN_COOKIE_NAME);
        return new Success("Successfully logged in.");
    }

    @GetMapping("/logout")
    public Success logout(HttpServletRequest request, HttpServletResponse response) {
        cookieHelper.deleteCookie(request,response,Constants.ADMIN_COOKIE_NAME);
        return new Success("Successfully logged out.");
    }

    @GetMapping("/profile")
    public Users getProfile(HttpServletRequest request) throws CustomUnauthorizedException {
        return sessionCheck(request);
    }

    @GetMapping("/clients")
    public List<Users> listAllClients(HttpServletRequest request)
            throws CustomUnauthorizedException {

        sessionCheck(request);
        return userRepository.findAllByRole(Role.CLIENT);
    }

    @GetMapping("/engineers")
    public List<Users> listAllEngineers(HttpServletRequest request)
            throws CustomUnauthorizedException {

        sessionCheck(request);
        return userRepository.findAllByRole(Role.ENGINEER);
    }

    @GetMapping("/engineers/approved")
    public List<Users> listAllApprovedEngineers(HttpServletRequest request)
            throws CustomUnauthorizedException {
        sessionCheck(request);
        return userRepository.findAllByRoleAndStatusAndActiveTrue(Role.ENGINEER, com.dreamhome.table.enumeration.ApproveReject.APPROVED);
    }

    @PostMapping("/approve/client")
    public Success approveOrRejectClient(@RequestBody ApproveReject approveReject, HttpServletRequest request)
            throws CustomUnauthorizedException, CustomBadRequestException {

        sessionCheck(request);
        Users client = userRepository.findByIdAndRole(approveReject.getId(),Role.CLIENT);
        if (client == null)
            throw new CustomBadRequestException("User not found.");

        if (approveReject.isApproveOrReject())
            client.setStatus(com.dreamhome.table.enumeration.ApproveReject.APPROVED);
        else
            client.setStatus(com.dreamhome.table.enumeration.ApproveReject.REJECTED);
        userRepository.save(client);
        return new Success("Successfully approved user.");
    }

    @PostMapping("/approve/engineer")
    public Success approveOrRejectEngineer(@RequestBody ApproveReject approveReject, HttpServletRequest request)
            throws CustomUnauthorizedException, CustomBadRequestException {

        sessionCheck(request);
        Users engineer = userRepository.findByIdAndRole(approveReject.getId(),Role.ENGINEER);
        if (engineer == null)
            throw new CustomBadRequestException("User not found.");

        if (approveReject.isApproveOrReject())
            engineer.setStatus(com.dreamhome.table.enumeration.ApproveReject.APPROVED);
        else
            engineer.setStatus(com.dreamhome.table.enumeration.ApproveReject.REJECTED);
        userRepository.save(engineer);
        return new Success("Successfully approved user.");
    }

    @GetMapping("/jobs")
    public List<Project> projectList(HttpServletRequest request) throws CustomUnauthorizedException {
        sessionCheck(request);
        return projectRepository.findAllByStatus(ProjectStatus.UNASSIGNED);
    }

    @PostMapping("/assign")
    public Success assignEngineerToProject(@RequestBody Assign assign,HttpServletRequest request) throws CustomUnauthorizedException {

        sessionCheck(request);
        Project project = projectRepository.findOneById(assign.getProjectId());
        if (project == null) throw new CustomUnauthorizedException("Project not found.");

        Users engineer = userRepository.findByIdAndRole(assign.getEngineer(),Role.ENGINEER);
        if (engineer == null) throw new CustomUnauthorizedException("Engineer not found.");

        project.setEngineerId(engineer.getId());
        project.setStatus(ProjectStatus.ASSIGNED);
        projectRepository.save(project);
        return new Success("Successfully assigned project.");
    }

    private Users sessionCheck(HttpServletRequest request) throws CustomUnauthorizedException {
        String cookieId = cookieHelper.getCookieValue(request,Constants.ADMIN_COOKIE_NAME);
        Users users = userRepository.findByCookie(UUID.fromString(cookieId));
        if (users != null)
            return users;
        else throw new CustomUnauthorizedException("Unauthorized.");
    }
}
