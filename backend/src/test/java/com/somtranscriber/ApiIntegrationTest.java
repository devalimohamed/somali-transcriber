package com.somtranscriber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.somtranscriber.auth.model.UserEntity;
import com.somtranscriber.auth.model.UserRole;
import com.somtranscriber.auth.model.UserStatus;
import com.somtranscriber.auth.repo.InviteRepository;
import com.somtranscriber.auth.repo.RefreshTokenRepository;
import com.somtranscriber.auth.repo.UserRepository;
import com.somtranscriber.calls.repo.CallRecordRepository;
import com.somtranscriber.processing.repo.JobAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private JobAttemptRepository jobAttemptRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanState() {
        jobAttemptRepository.deleteAll();
        callRecordRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        inviteRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void loginRefreshAndLogoutFlow() throws Exception {
        createUser("worker@example.com", "123456", UserRole.WORKER);

        String loginResponse = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"worker@example.com","pin":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String refreshToken = loginJson.get("refreshToken").asText();

        String refreshResponse = mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String rotatedRefresh = objectMapper.readTree(refreshResponse).get("refreshToken").asText();
        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotatedRefresh + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void inviteCreationAndAcceptanceFlow() throws Exception {
        createUser("operator@example.com", "123456", UserRole.OPERATOR);
        String operatorAccess = accessToken("operator@example.com", "123456");

        String inviteResponse = mockMvc.perform(post("/v1/invites")
                        .header("Authorization", "Bearer " + operatorAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.worker@example.com\",\"expiresInHours\":24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String inviteToken = objectMapper.readTree(inviteResponse).get("inviteToken").asText();

        mockMvc.perform(post("/v1/invites/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"" + inviteToken + "\",\"pin\":\"234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void createUploadEditAndFinalizeCall() throws Exception {
        createUser("worker@example.com", "123456", UserRole.WORKER);
        String access = accessToken("worker@example.com", "123456");

        String callResponse = mockMvc.perform(post("/v1/calls")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "callAt":"%s"
                                }
                                """.formatted(Instant.now().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn().getResponse().getContentAsString();

        UUID callId = UUID.fromString(objectMapper.readTree(callResponse).get("callId").asText());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "call.m4a",
                "audio/mpeg",
                "dummy-audio".getBytes()
        );

        mockMvc.perform(multipart("/v1/calls/{callId}/audio", callId)
                        .file(file)
                        .param("durationSeconds", "30")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.noteText").isNotEmpty());

        mockMvc.perform(patch("/v1/calls/{callId}/draft", callId)
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteText\":\"Called clinic, confirmed patient follow-up tomorrow.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteText").value("Called clinic, confirmed patient follow-up tomorrow."));

        mockMvc.perform(post("/v1/calls/{callId}/finalize", callId)
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.isFinal").value(true));
    }

    @Test
    void rejectsAudioLongerThanTwoMinutes() throws Exception {
        createUser("worker@example.com", "123456", UserRole.WORKER);
        String access = accessToken("worker@example.com", "123456");

        String callResponse = mockMvc.perform(post("/v1/calls")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callAt":"%s"}
                                """.formatted(Instant.now().toString())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UUID callId = UUID.fromString(objectMapper.readTree(callResponse).get("callId").asText());

        MockMultipartFile file = new MockMultipartFile("file", "long.m4a", "audio/mpeg", "audio".getBytes());

        mockMvc.perform(multipart("/v1/calls/{callId}/audio", callId)
                        .file(file)
                        .param("durationSeconds", "121")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preventsCrossUserCallAccess() throws Exception {
        createUser("a@example.com", "123456", UserRole.WORKER);
        createUser("b@example.com", "123456", UserRole.WORKER);

        String accessA = accessToken("a@example.com", "123456");
        String accessB = accessToken("b@example.com", "123456");

        String callResponse = mockMvc.perform(post("/v1/calls")
                        .header("Authorization", "Bearer " + accessA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callAt":"%s"}
                                """.formatted(Instant.now().toString())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UUID callId = UUID.fromString(objectMapper.readTree(callResponse).get("callId").asText());

        mockMvc.perform(get("/v1/calls/{callId}", callId)
                        .header("Authorization", "Bearer " + accessB))
                .andExpect(status().isNotFound());
    }

    private UserEntity createUser(String email, String password, UserRole role) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private String accessToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"pin\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
