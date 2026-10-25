package com.clinica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ponto de entrada do backend. */
@SpringBootApplication
public class ClinicaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClinicaApplication.class, args); // Inicia o servidor na porta 8080
    }
}
package com.clinica.entity;

import jakarta.persistence.*;
import lombok.*;

/** Usuário do sistema (triagem ou médico). Vira a tabela "usuarios". */
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

/** Paciente. A chave primária é o próprio CPF. */
@Entity
@Table(name = "pacientes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Paciente {

    @Id
    @Column(length = 11)
    private String cpf;                 // Só números

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

/** Atendimento (triagem). Status inicial: "Aguardando". */
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

    @Column(nullable = false)
    private String urgencia;            // Emergência, Muito Urgente, etc.

    @Column(nullable = false)
    private String status;              // Aguardando / Em atendimento / Finalizado

    @Column(length = 1000)
    private String diagnostico;         // Preenchido pelo médico

    @Column(length = 2000)
    private String conduta;             // Preenchido pelo médico

    @Column(length = 2000)
    private String observacoes;         // Preenchido pelo médico

    @Column(length = 2000)
    private String prescricao;          // Receita
}
package com.clinica.repository;

import com.clinica.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsuario(String usuario);
    boolean existsByUsuario(String usuario);
}
package com.clinica.repository;

import com.clinica.entity.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PacienteRepository extends JpaRepository<Paciente, String> {
}
package com.clinica.repository;

import com.clinica.entity.Atendimento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {
    List<Atendimento> findByStatusOrderByDataAsc(String status);  // Fila ordenada
    List<Atendimento> findByCpfOrderByDataDesc(String cpf);       // Histórico do paciente
    List<Atendimento> findAllByOrderByDataAsc();                  // Todos, por ordem de chegada
}
package com.clinica.service;

import com.clinica.entity.Usuario;
import com.clinica.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Cadastra novo usuário (criptografa a senha)
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
                .senha(passwordEncoder.encode(dados.get("senha"))) // BCrypt
                .perfil(perfil)
                .build();

        usuarioRepository.save(usuario);
        return Map.of("mensagem", "Conta criada com sucesso.", "usuario", usuario.getUsuario());
    }

    // Faz login: confere usuário e senha e devolve os dados para o HTML
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
package com.clinica.controller;

import com.clinica.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// AuthController – público
@CrossOrigin                     // Permite o HTML chamar o Java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/cadastro")   // Criar conta
    public Map<String, Object> cadastro(@RequestBody Map<String, String> dados) {
        return authService.cadastrar(dados);
    }

    @PostMapping("/login")      // Login
    public Map<String, Object> login(@RequestBody Map<String, String> dados) {
        return authService.login(dados);
    }
}
package com.clinica.controller;

import com.clinica.entity.Paciente;
import com.clinica.repository.AtendimentoRepository;
import com.clinica.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

// PacienteController
@CrossOrigin
@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteRepository pacienteRepository;
    private final AtendimentoRepository atendimentoRepository;

    @GetMapping("/{cpf}")       // Buscar paciente (triagem + médico)
    public Map<String, Object> buscar(@PathVariable String cpf) {
        String cpfLimpo = cpf.replaceAll("\\D", "");   // Tira pontos e traço

        Paciente paciente = pacienteRepository.findById(cpfLimpo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("paciente", paciente);
        resposta.put("historico", atendimentoRepository.findByCpfOrderByDataDesc(cpfLimpo));
        return resposta;
    }

    @PostMapping                // Cadastrar paciente (só triagem)
    public Paciente cadastrar(@RequestBody Map<String, String> dados) {
        String cpf = dados.get("cpf").replaceAll("\\D", "");

        if (cpf.length() != 11) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CPF deve ter 11 números.");
        }
        if (pacienteRepository.existsById(cpf)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paciente já cadastrado.");
        }

        String alergias = dados.get("alergias");
        if (alergias == null || alergias.isBlank()) alergias = "Nenhuma informada";

        return pacienteRepository.save(Paciente.builder()
                .cpf(cpf)
                .nome(dados.get("nome").trim())
                .dataNascimento(LocalDate.parse(dados.get("dataNascimento")))
                .alergias(alergias.trim())
                .build());
    }
}
package com.clinica.controller;

import com.clinica.entity.Atendimento;
import com.clinica.entity.Paciente;
import com.clinica.repository.AtendimentoRepository;
import com.clinica.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

// AtendimentoController
@CrossOrigin
@RestController
@RequestMapping("/api/atendimentos")
@RequiredArgsConstructor
public class AtendimentoController {

    private final AtendimentoRepository atendimentoRepository;
    private final PacienteRepository pacienteRepository;

    @PostMapping                // Registrar triagem (só triagem)
    public Atendimento registrar(@RequestBody Map<String, String> dados) {
        String cpf = dados.get("cpf").replaceAll("\\D", "");

        if (!pacienteRepository.existsById(cpf)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não cadastrado.");
        }

        return atendimentoRepository.save(Atendimento.builder()
                .cpf(cpf)
                .data(LocalDateTime.now())              // Hora de chegada = agora
                .sintomas(dados.get("sintomas").trim())
                .urgencia(dados.get("urgencia"))
                .status("Aguardando")
                .build());
    }

    @GetMapping("/fila")        // Ver fila (só médico)
    public List<Map<String, Object>> fila() {
        List<Map<String, Object>> lista = new ArrayList<>();

        // Em ordem de chegada, juntando o atendimento com os dados do paciente
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

    @PatchMapping("/{id}/status")       // Muda o status (ex.: "Em atendimento")
    public Atendimento mudarStatus(@PathVariable Long id, @RequestBody Map<String, String> dados) {
        Atendimento a = buscar(id);
        a.setStatus(dados.get("status"));
        return atendimentoRepository.save(a);
    }

    @PatchMapping("/{id}/prescricao")   // Finaliza: salva diagnóstico, receita etc.
    public Atendimento finalizar(@PathVariable Long id, @RequestBody Map<String, String> dados) {
        Atendimento a = buscar(id);
        a.setDiagnostico(dados.get("diagnostico"));
        a.setConduta(dados.get("conduta"));
        a.setObservacoes(dados.get("observacoes"));
        a.setPrescricao(dados.get("prescricao"));
        a.setStatus("Finalizado");
        return atendimentoRepository.save(a);
    }

    // Auxiliar: busca o atendimento ou dá erro 404
    private Atendimento buscar(Long id) {
        return atendimentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento não encontrado."));
    }
}
server.error.include-message=always
    
