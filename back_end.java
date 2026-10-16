package com.clinica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal da aplicação Spring Boot.
 * Ponto de entrada do backend.
 */
@SpringBootApplication
public class ClinicaApplication {

    public static void main(String[] args) {
        // Inicia o servidor na porta 8080
        SpringApplication.run(ClinicaApplication.class, args);
    }
}
package com.clinica.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Representa um usuário do sistema (triagem ou médico).
 * Vira a tabela "usuarios" no banco.
 */
@Entity
@Table(name = "usuarios")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                    // ID automático

    @Column(nullable = false)
    private String nome;                // Nome completo

    @Column(nullable = false, unique = true)
    private String usuario;             // Login (único)

    @Column(nullable = false)
    private String senha;               // Senha criptografada (BCrypt)

    @Column(nullable = false)
    private String perfil;              // "triagem" ou "medico"
}
package com.clinica.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Representa um paciente.
 * A chave primária é o próprio CPF.
 */
@Entity
@Table(name = "pacientes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Paciente {

    @Id
    @Column(length = 11)
    private String cpf;                 // Só números (chave primária)

    @Column(nullable = false)
    private String nome;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(length = 500)
    private String alergias;            // "Nenhuma informada" se vazio
}
package com.clinica.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Representa um atendimento (triagem).
 * Status inicial: "Aguardando"
 */
@Entity
@Table(name = "atendimentos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Atendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 11)
    private String cpf;

    @Column(nullable = false)
    private LocalDateTime data;         // Data/hora do registro

    @Column(nullable = false, length = 2000)
    private String sintomas;
// UsuarioRepository.java
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsuario(String usuario);
    boolean existsByUsuario(String usuario);
}

// PacienteRepository.java
public interface PacienteRepository extends JpaRepository<Paciente, String> {
}@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // Cadastra novo usuário (criptografa a senha)
    public Map<String, Object> cadastrar(CadastroUsuarioRequest request) {
        String login = request.getUsuario().toLowerCase().trim();

        if (usuarioRepository.existsByUsuario(login)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este usuário já existe.");
        }

        String perfil = request.getPerfil().toLowerCase().trim();
        if (!perfil.equals("triagem") && !perfil.equals("medico")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil inválido.");
        }

        Usuario usuario = Usuario.builder()
                .nome(request.getNome().trim())
                .usuario(login)
                .senha(passwordEncoder.encode(request.getSenha())) // BCrypt
                .perfil(perfil)
                .build();

        usuarioRepository.save(usuario);

        return Map.of("mensagem", "Conta criada com sucesso.", "usuario", usuario.getUsuario());
    }

    // Faz login e gera o token JWT
    public Map<String, Object> login(LoginRequest request) {
        String login = request.getUsuario().toLowerCase().trim();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(login, request.getSenha())
        );

        Usuario usuario = usuarioRepository.findByUsuario(login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos."));

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(usuario.getUsuario())
                .password(usuario.getSenha())
                .roles(usuario.getPerfil().toUpperCase())
                .build();

        Map<String, Object> claims = new HashMap<>();
        claims.put("nome", usuario.getNome());
        claims.put("perfil", usuario.getPerfil());

        String token = jwtService.generateToken(claims, userDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("nome", usuario.getNome());
        response.put("usuario", usuario.getUsuario());
        response.put("perfil", usuario.getPerfil());
        return response;
    }
}
  // AuthController – público
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @PostMapping("/cadastro")   // Criar conta
    @PostMapping("/login")      // Login → retorna token
}

// PacienteController – precisa de token
@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {
    @GetMapping("/{cpf}")       // Buscar paciente (triagem + médico)
    @PostMapping                // Cadastrar paciente (só triagem)
}

// AtendimentoController – precisa de token
@RestController
@RequestMapping("/api/atendimentos")
public class AtendimentoController {
    @PostMapping                // Registrar triagem (só triagem)
    @GetMapping("/fila")        // Ver fila (só médico)
    @PatchMapping("/{id}/status")
    @PatchMapping("/{id}/prescricao")
}
  cd clinica-backend
mvn spring-boot:run

// AtendimentoRepository.java
public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {
    List<Atendimento> findByStatusOrderByDataAsc(String status);  // Fila ordenada
    List<Atendimento> findByCpfOrderByDataDesc(String cpf);
}
  
    @Column(nullable = false)
    private String urgencia;            // Emergência, Muito Urgente, etc.

    @Column(length = 2000)
    private String prescricao;

    @Column(nullable = false)
    private String status;              // Aguardando / Em atendimento / Finalizado
}

