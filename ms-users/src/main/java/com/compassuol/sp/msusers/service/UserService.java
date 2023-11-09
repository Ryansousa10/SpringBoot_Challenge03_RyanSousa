package com.compassuol.sp.msusers.service;

import com.compassuol.sp.msusers.dto.LoginRequestDTO;
import com.compassuol.sp.msusers.dto.LoginResponseDTO;
import com.compassuol.sp.msusers.dto.UserDTO;
import com.compassuol.sp.msusers.exception.*;
import com.compassuol.sp.msusers.model.User;
import com.compassuol.sp.msusers.repository.UserRepository;
import com.compassuol.sp.msusers.util.JwtTokenProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private Validator validator;

    @Autowired
    private final JwtTokenProvider jwtTokenProvider;

    public UserDTO createUser(UserDTO userDTO) {
        validateUserDTO(userDTO);
        validateNameLength(userDTO.getFirstName(), "firstName");
        validateNameLength(userDTO.getLastName(), "lastName");
        validatePasswordLength(userDTO.getPassword());
        validateUniqueCpf(userDTO.getCpf());

        if (userDTO.getCpf() != null) {
            validateCpfFormat(userDTO.getCpf());
        } else {
            throw new InvalidCpfFormatException("CPF inválido");
        }

        validateUniqueEmail(userDTO.getEmail());
        validateActiveValue(userDTO.isActive());

        User user = userMapper.mapUserDTOToUser(userDTO);
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));

        User savedUser = userRepository.save(user);

        return userMapper.mapUserToUserDTO(savedUser);
    }

    public UserDTO getUserById(Long id) {
        Optional<User> userOptional = userRepository.findById(id);
        return userOptional.map(userMapper::mapUserToUserDTO).orElse(null);
    }

    public UserDTO updateUser(Long id, UserDTO userDTO) {
        Set<ConstraintViolation<UserDTO>> dtoViolations = validator.validate(userDTO);
        if (!dtoViolations.isEmpty()) {
        }

        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isPresent()) {
            User existingUser = userOptional.get();

            User updatedUser = userMapper.mapUserDTOToUser(userDTO);

            existingUser.setFirstName(updatedUser.getFirstName());
            existingUser.setLastName(updatedUser.getLastName());
            existingUser.setEmail(updatedUser.getEmail());
            existingUser.setCpf(updatedUser.getCpf());
            existingUser.setBirthdate(updatedUser.getBirthdate());
            existingUser.setActive(updatedUser.isActive());

            Set<ConstraintViolation<User>> entityViolations = validator.validate(existingUser);
            if (!entityViolations.isEmpty()) {
            }

            User savedUser = userRepository.save(existingUser);

            return userMapper.mapUserToUserDTO(savedUser);
        } else {
            return null;
        }
    }

    public void updatePassword(Long id, String newPassword) {
        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
        }
    }

    public ResponseEntity<LoginResponseDTO> login(LoginRequestDTO loginRequest) {
        try {
            UserDetails user = isValidUser(loginRequest.getEmail(), loginRequest.getPassword());
            var token = jwtTokenProvider.generateToken(loginRequest.getEmail());
            String tokenType = "Bearer";
            String username = loginRequest.getEmail();
            LoginResponseDTO responseDTO = new LoginResponseDTO(token, tokenType, username);
            return ResponseEntity.ok(responseDTO);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    public UserDetails isValidUser(String email, String password) {
        UserDetails user = userRepository.findByEmail(email);

        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        } else {
            throw new UsernameNotFoundException("Usuário não encontrado ou senha incorreta");
        }
    }

    public void validateUserDTO(UserDTO userDTO) {
        Set<ConstraintViolation<UserDTO>> dtoViolations = validator.validate(userDTO);
        if (!dtoViolations.isEmpty()) {
            throw new InvalidUserDTOException("Invalid UserDTO");
        }
    }

    void validateNameLength(String name, String fieldName) {
        if (name != null && name.length() < 3) {
            throw new InvalidNameLengthException("O campo '" + fieldName + "' deve ter pelo menos 3 caracteres.");
        }
    }

    void validatePasswordLength(String password) {
        if (password != null && password.length() < 6) {
            throw new InvalidPasswordLengthException("A senha deve ter no mínimo 6 caracteres.");
        }
    }

    void validateUniqueCpf(String cpf) {
        if (userRepository.existsByCpf(cpf)) {
            throw new DuplicateCpfException("CPF duplicado. Um usuário com o mesmo CPF já existe.");
        }
    }

    void validateCpfFormat(String cpf) {
        if (!isCPFInFormat(cpf)) {
            throw new InvalidCpfFormatException("O CPF não está no formato correto (000.000.000-00).");
        }
    }

    void validateUniqueEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email duplicado. Já existe um usuário com o mesmo email.");
        }
    }

    void validateActiveValue(Boolean active) {
        if (active == null || (active != true && active != false)) {
            throw new InvalidActiveValueException("O campo 'active' deve conter somente valores 'true' ou 'false'.");
        }
    }

    public static boolean isCPFInFormat(String cpf) {
        String cpfNumerico = cpf.replaceAll("[^0-9]", "");

        if (cpfNumerico.length() != 11) {
            return false;
        }

        return Pattern.matches("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}", cpf);
    }
}