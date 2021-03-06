package pas.spring.demos.controllers;

import javax.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import pas.spring.demos.entities.User;
import pas.spring.demos.repositories.UserRepository;

import co.elastic.apm.api.Transaction;
import co.elastic.apm.api.ElasticApm;

import java.util.List;

@Slf4j
@Controller
public class UserController {

    private final UserRepository userRepository;

    @Autowired
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute("transaction")
    public Transaction transaction() {
        return ElasticApm.currentTransaction();
    }

    @ModelAttribute("apmServer")
    public String apmServer() {
        return System.getenv("ELASTIC_APM_SERVER_URLS");
    }

    @GetMapping("/signup")
    public String showSignUpForm(User user) {
        return "add-user";
    }

    @PostMapping("/adduser")
    public String addUser(@Valid User user, BindingResult result, Model model) {
        if (result.hasErrors()) {
            log.info("Add new user invoked");
            return "add-user";
        }

        userRepository.save(user);
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("userCount", userRepository.count());
        return "index";
    }

    @GetMapping("/edit/{id}")
    public String showUpdateForm(@PathVariable("id") long id, Model model) {
        log.info("Edit user invoked");
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        model.addAttribute("user", user);
        model.addAttribute("userCount", userRepository.count());
        return "update-user";
    }

    @PostMapping("/update/{id}")
    public String updateUser(@PathVariable("id") long id, @Valid User user, BindingResult result, Model model) {
        log.info("Update user invoked");
        if (result.hasErrors()) {
            user.setId(id);
            return "update-user";
        }

        userRepository.save(user);
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("userCount", userRepository.count());
        return "redirect:/";
    }

    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable("id") long id, Model model) {
        log.info("Delete user invoked");
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        userRepository.delete(user);
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("userCount", userRepository.count());
        return "redirect:/";
    }

    @GetMapping("/")
    public String indexPage (Model model){
        log.info("Home page invoked");
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("userCount", userRepository.count());
        return "index";
    }

    @PostMapping("/search")
    public String findByName (@RequestParam(value="name") String name, Model model){

        log.info("Search invoked");
        List<User> bookSearchList = userRepository.findByNameContaining(name);

        model.addAttribute("users", bookSearchList);
        model.addAttribute("userCount", bookSearchList.size());
        return "index";
    }

    @GetMapping("/500error")
    public String userError (Model model) {
        log.info("Invoking a HTTP 500 error now");
        throw new RuntimeException("error looking up database");
    }
}

