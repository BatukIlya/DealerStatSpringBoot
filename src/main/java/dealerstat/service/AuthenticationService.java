package dealerstat.service;

import dealerstat.dto.AuthenticationRequestDto;
import dealerstat.dto.MyUserDto;
import dealerstat.entity.MyUser;
import dealerstat.entity.Role;
import dealerstat.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;

    private final JwtTokenProvider jwtTokenProvider;

    private final MyUserService myUserService;

    private final RedisService redisService;

    private final BCryptPasswordEncoder passwordEncoder;

    private final EmailSenderService emailSenderService;

    @Value("${spring.mail.username}")
    private String emailSender;

    @Value("${server.port}")
    private String port;

    public ResponseEntity<?> registerUser(@RequestBody @Valid MyUserDto myUserDto) {
        String email = (String) redisService.getToken(myUserDto.getEmail());
        System.out.println(email);
        MyUser myUser = myUserService.findMyUserByEmail(myUserDto.getEmail());
        if (myUser == null && email == null) {
            MyUser myUser1 = new MyUser();
            myUser1.setFirstName(myUserDto.getFirstName());
            myUser1.setLastName(myUserDto.getLastName());
            myUser1.setPassword(passwordEncoder.encode(myUserDto.getPassword()));
            myUser1.setEmail(myUserDto.getEmail().toLowerCase(Locale.ROOT));
            myUser1.setRoles(Collections.singletonList(Role.TRADER));
            myUser1.setRating(0.0);

            String token = UUID.randomUUID().toString();

            messageSender(myUserDto.getEmail(), token, "confirm");

            redisService.putToken(token, myUser1);
            redisService.putToken(myUserDto.getEmail(), "CHECK");

            return ResponseEntity.ok("To complete the registration, check your email, please.");
        } else if (myUser != null) {
            return ResponseEntity.status(404).body("User with email " + myUser.getEmail() + " already exist");
        } else {
            return ResponseEntity.status(404).body("Check your email you have already registered");
        }
    }

    public ResponseEntity<?> login(@RequestBody @Valid AuthenticationRequestDto requestDto) {
        try {
            String email = requestDto.getEmail().toLowerCase(Locale.ROOT);
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, requestDto.getPassword()));
            MyUser user = myUserService.findMyUserByEmail(email);

            if (user == null) {
                return ResponseEntity.status(404).body("User with email " + email + " not found");
            } else if (!user.isApproved()) {
                return ResponseEntity.status(403).body("Admin hasn't yet confirmed your request");
            } else {

                String token = jwtTokenProvider.createToken(email, user.getId(), user.getRoles());

                Map<Object, Object> response = new HashMap<>();
                response.put("username", email);
                response.put("token", token);

                return ResponseEntity.ok(response);
            }
        } catch (AuthenticationException e) {
            return ResponseEntity.status(404).body("Invalid email or password");
        }
    }


    public ResponseEntity<?> confirmAccount(String token) {
        MyUser myUser = (MyUser) redisService.getToken(token);
        if (myUser != null) {
            redisService.deleteToken(myUser.getEmail());
            redisService.deleteToken(token);
            myUserService.save(myUser);
            return ResponseEntity.ok("Complete registration! Please, wait for admin confirmation");
        } else {
            return ResponseEntity.status(404).body("The token's lifetime has expired");
        }
    }

    public ResponseEntity<?> sendMessageForSetPassword(String email) {
        MyUser myUser = myUserService.findMyUserByEmail(email);
        if (myUser != null) {
            String token = UUID.randomUUID().toString();

            messageSender(email, token, "set_password");

            redisService.putToken(token, email);

            return ResponseEntity.ok("Confirmation email has been sent to your email");
        } else {
            return ResponseEntity.status(404).body("User not found");
        }
    }

    public ResponseEntity<?> setPassword(String password, String token) {
        String email = (String) redisService.getToken(token);
        if (email != null) {
            redisService.deleteToken(token);
            MyUser myUser = myUserService.findMyUserByEmail(email);
            myUser.setPassword(passwordEncoder.encode(password));
            myUserService.save(myUser);
            return ResponseEntity.ok("Your password has been successfully changed!");
        } else {
            return ResponseEntity.status(404).body("The token's lifetime has expired");
        }
    }

    private void messageSender(String email, String token, String url){
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(email);
        mailMessage.setSubject("Please, confirm your email");
        mailMessage.setFrom(emailSender);
        mailMessage.setText("To confirm your account, please click here : "
                + "http://localhost:" + port + "/auth/" + url + "?token=" + token);

        emailSenderService.sendEmail(mailMessage);
    }


}
