package com.clinica;

import com.clinica.entity.Atendimento;
import com.clinica.entity.Paciente;
import com.clinica.entity.Usuario;
import com.clinica.repository.AtendimentoRepository;
import com.clinica.repository.PacienteRepository;
import com.clinica.repository.UsuarioRepository;
import com.clinica.service.AuthService;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@SpringBootApplication
public class ClinicaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClinicaApplication.class, args);
    }
}

// =========================
// ENTIDADES
// =========================

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String usuario;

    @Column(nullable = false)
    private String senha;

    @Column(nullable = false)
    private String perfil;
}

@Entity
@Table(name = "pacientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Paciente {

    @Id
    @Column(length = 11)
    private String cpf;

    @Column(nullable = false)
    private String nome;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(length = 500)
    private String alergias;
}

@Entity
@Table(name = "atendimentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Atendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 11)
    private String cpf;

    @Column(nullable = false)
    private LocalDateTime data;

    @Column(nullable = false, length = 2000)
    private String sintomas;

    @Column(nullable = false)
    private String urgencia;

    @Column(nullable = false)
    private String status;

    @Column(length = 1000)
    private String diagnostico;

    @Column(length = 2000)
    private String conduta;

    @Column(length = 2000)
    private String observacoes;

    @Column(length = 2000)
    private String prescricao;
}

// =========================
// REPOSITÓRIOS
// =========================

interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsuario(String usuario);
    boolean existsByUsuario(String usuario);
}

interface PacienteRepository extends JpaRepository<Paciente, String> {
}

interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {
    List<Atendimento> findByStatusOrderByDataAsc(String status);
    List<Atendimento> findByCpfOrderByDataDesc(String cpf);
    List<Atendimento> findAllByOrderByDataAsc();
}

// =========================
// SERVIÇOS
// =========================

@Service
@RequiredArgsConstructor
class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Map<String, Object> cadastrar(Map<String, String> dados) {
        String login = dados.get("usuario").toLowerCase().trim();

        if (usuarioRepository.existsByUsuario(login)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este usuário já existe.");
        }

        String perfil = dados.get("perfil").toLowerCase().trim();
        if (!perfil.equals("triagem") && !perfil.equals("medico")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil inválido.");
        }

        Usuario usuario = Usuario.builder()
                .nome(dados.get("nome").trim())
                .usuario(login)
                .senha(passwordEncoder.encode(dados.get("senha")))
                .perfil(perfil)
                .build();

        usuarioRepository.save(usuario);

        return Map.of(
                "mensagem", "Conta criada com sucesso.",
                "usuario", usuario.getUsuario()
        );
    }

    public Map<String, Object> login(Map<String, String> dados) {
        String login = dados.get("usuario").toLowerCase().trim();

        Usuario usuario = usuarioRepository.findByUsuario(login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos."));

        if (!passwordEncoder.matches(dados.get("senha"), usuario.getSenha())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos.");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("nome", usuario.getNome());
        response.put("usuario", usuario.getUsuario());
        response.put("perfil", usuario.getPerfil());

        return response;
    }
}

// =========================
// CONTROLLERS
// =========================

@CrossOrigin
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthService authService;

    @PostMapping("/cadastro")
    public Map<String, Object> cadastro(@RequestBody Map<String, String> dados) {
        return authService.cadastrar(dados);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> dados) {
        return authService.login(dados);
    }
}

@CrossOrigin
@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
class PacienteController {

    private final PacienteRepository pacienteRepository;
    private final AtendimentoRepository atendimentoRepository;

    @GetMapping("/{cpf}")
    public Map<String, Object> buscar(@PathVariable String cpf) {
        String cpfLimpo = cpf.replaceAll("\\D", "");

        Paciente paciente = pacienteRepository.findById(cpfLimpo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("paciente", paciente);
        resposta.put("historico", atendimentoRepository.findByCpfOrderByDataDesc(cpfLimpo));
        return resposta;
    }

    @PostMapping
    public Paciente cadastrar(@RequestBody Map<String, String> dados) {
        String cpf = dados.get("cpf").replaceAll("\\D", "");

        if (cpf.length() != 11) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CPF deve ter 11 números.");
        }

        if (pacienteRepository.existsById(cpf)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paciente já cadastrado.");
        }

        String alergias = dados.get("alergias");
        if (alergias == null || alergias.isBlank()) {
            alergias = "Nenhuma informada";
        }

        return pacienteRepository.save(Paciente.builder()
                .cpf(cpf)
                .nome(dados.get("nome").trim())
                .dataNascimento(LocalDate.parse(dados.get("dataNascimento")))
                .alergias(alergias.trim())
                .build());
    }
}

@CrossOrigin
@RestController
@RequestMapping("/api/atendimentos")
@RequiredArgsConstructor
class AtendimentoController {

    private final AtendimentoRepository atendimentoRepository;
    private final PacienteRepository pacienteRepository;

    @PostMapping
    public Atendimento registrar(@RequestBody Map<String, String> dados) {
        String cpf = dados.get("cpf").replaceAll("\\D", "");

        if (!pacienteRepository.existsById(cpf)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não cadastrado.");
        }

        return atendimentoRepository.save(Atendimento.builder()
                .cpf(cpf)
                .data(LocalDateTime.now())
                .sintomas(dados.get("sintomas").trim())
                .urgencia(dados.get("urgencia"))
                .status("Aguardando")
                .build());
    }

    @GetMapping("/fila")
    public List<Map<String, Object>> fila() {
        List<Map<String, Object>> lista = new ArrayList<>();

        for (Atendimento a : atendimentoRepository.findAllByOrderByDataAsc()) {
            Paciente p = pacienteRepository.findById(a.getCpf()).orElse(null);

            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("cpf", a.getCpf());
            item.put("nome", p != null ? p.getNome() : "Desconhecido");
            item.put("alergias", p != null ? p.getAlergias() : "");
            item.put("dataNascimento", p != null ? p.getDataNascimento() : null);
            item.put("sintomas", a.getSintomas());
            item.put("urgencia", a.getUrgencia());
            item.put("status", a.getStatus());
            item.put("data", a.getData());
            lista.add(item);
        }
        return lista;
    }

    @PatchMapping("/{id}/status")
    public Atendimento mudarStatus(@PathVariable Long id, @RequestBody Map<String, String> dados) {
        Atendimento a = buscar(id);
        a.setStatus(dados.get("status"));
        return atendimentoRepository.save(a);
    }

    @PatchMapping("/{id}/prescricao")
    public Atendimento finalizar(@PathVariable Long id, @RequestBody Map<String, String> dados) {
        Atendimento a = buscar(id);
        a.setDiagnostico(dados.get("diagnostico"));
        a.setConduta(dados.get("conduta"));
        a.setObservacoes(dados.get("observacoes"));
        a.setPrescricao(dados.get("prescricao"));
        a.setStatus("Finalizado");
        return atendimentoRepository.save(a);
    }

    private Atendimento buscar(Long id) {
        return atendimentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento não encontrado."));
    }
}
