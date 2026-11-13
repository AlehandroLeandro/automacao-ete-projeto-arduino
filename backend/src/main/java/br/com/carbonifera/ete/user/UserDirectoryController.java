package br.com.carbonifera.ete.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/directory")
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
public class UserDirectoryController {

    private final UserService userService;

    public UserDirectoryController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDirectoryEntry> list() {
        return userService.listDirectory();
    }
}
