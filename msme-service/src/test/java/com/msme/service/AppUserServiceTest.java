package com.msme.service;

import com.msme.dto.user.AppUserResponse;
import com.msme.dto.user.CreateMsmeRepRequest;
import com.msme.dto.user.UpdateMsmeRepRequest;
import com.msme.entity.AppUser;
import com.msme.entity.Msme;
import com.msme.exception.DuplicateResourceException;
import com.msme.exception.ForbiddenOperationException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.repository.AppUserRepository;
import com.msme.repository.MsmeRepository;
import com.msme.security.CustomUserPrincipal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private MsmeRepository msmeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AppUserService service;

    private Msme msme;
    private AppUser representative;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        msme = new Msme();
        msme.setMsmeId(100L);
        msme.setBusinessName("ABC Industries");
        msme.setAssignedRmId(10L);
        msme.setIsActive("Y");

        representative = new AppUser();
        representative.setUserId(20L);
        representative.setName("Representative");
        representative.setEmail("rep@example.com");
        representative.setPasswordHash("existing-hash");
        representative.setRole("MSME_REP");
        representative.setMsmeId(100L);
        representative.setAccountStatus("ACTIVE");
        representative.setCreatedAt(
                LocalDateTime.of(2026, 1, 1, 10, 0));
        representative.setUpdatedAt(representative.getCreatedAt());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserReturnsLoggedInUser() {
        login(20L, "MSME_REP", 100L);
        when(userRepository.findById(20L))
                .thenReturn(Optional.of(representative));

        AppUserResponse result = service.getCurrentUser();

        assertEquals(20L, result.getUserId());
        assertEquals("rep@example.com", result.getEmail());
        assertEquals("MSME_REP", result.getRole());
    }

    @Test
    void getCurrentUserRejectsMissingAuthentication() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> service.getCurrentUser());

        verifyNoInteractions(userRepository);
    }

    @Test
    void getCurrentUserRejectsMissingUser() {
        login(20L, "MSME_REP", 100L);
        when(userRepository.findById(20L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCurrentUser());
    }

    @Test
    void getAllUsersMapsRepositoryResults() {
        when(userRepository.findAll())
                .thenReturn(List.of(representative));

        List<AppUserResponse> result = service.getAllUsers();

        assertEquals(1, result.size());
        assertEquals(20L, result.get(0).getUserId());
    }

    @Test
    void getMyRepresentativesUsesOnlyAssignedMsmeIds() {
        login(10L, "RM", null);

        when(msmeRepository.findByAssignedRmId(10L))
                .thenReturn(List.of(msme));
        when(userRepository.findByRoleAndMsmeIdIn(
                "MSME_REP", List.of(100L)))
                .thenReturn(List.of(representative));

        List<AppUserResponse> result =
                service.getMyMsmeRepresentatives();

        assertEquals(1, result.size());
        assertEquals(20L, result.get(0).getUserId());

        verify(userRepository).findByRoleAndMsmeIdIn(
                "MSME_REP", List.of(100L));
    }

    @Test
    void getMyRepresentativesReturnsEmptyWhenNoMsmesAssigned() {
        login(10L, "RM", null);

        when(msmeRepository.findByAssignedRmId(10L))
                .thenReturn(List.of());

        assertTrue(service.getMyMsmeRepresentatives().isEmpty());

        verifyNoInteractions(userRepository);
    }

    @Test
    void createRepresentativeEncodesPasswordAndSetsBackendFields() {
        login(10L, "RM", null);
        existingMsme();

        when(passwordEncoder.encode("Password123!"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(AppUser.class)))
                .thenAnswer(invocation -> {
                    AppUser saved = invocation.getArgument(0);
                    saved.setUserId(20L);
                    return saved;
                });

        AppUserResponse result =
                service.createMsmeRepresentative(createRequest());

        ArgumentCaptor<AppUser> captor =
                ArgumentCaptor.forClass(AppUser.class);

        verify(userRepository).save(captor.capture());

        AppUser saved = captor.getValue();

        assertAll(
                () -> assertEquals(20L, result.getUserId()),
                () -> assertEquals("New Representative", saved.getName()),
                () -> assertEquals("new@example.com", saved.getEmail()),
                () -> assertEquals("encoded-password",
                        saved.getPasswordHash()),
                () -> assertEquals("MSME_REP", saved.getRole()),
                () -> assertEquals(100L, saved.getMsmeId()),
                () -> assertEquals("ACTIVE", saved.getAccountStatus()),
                () -> assertNotNull(saved.getCreatedAt()),
                () -> assertEquals(saved.getCreatedAt(), saved.getUpdatedAt())
        );

        verify(passwordEncoder).encode("Password123!");
    }

    @Test
    void createRepresentativeRejectsInactiveBusiness() {
        login(10L, "RM", null);
        msme.setIsActive("N");
        when(msmeRepository.findById(msme.getMsmeId())).thenReturn(Optional.of(msme));
        assertThrows(com.msme.exception.InvalidRequestException.class, ()->service.createMsmeRepresentative(createRequest()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createRepresentativeRejectsMissingMsme() {
        login(10L, "RM", null);

        when(msmeRepository.findById(100L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createMsmeRepresentative(createRequest()));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void createRepresentativeRejectsAnotherRmsMsme() {
        login(99L, "RM", null);
        existingMsme();

        assertThrows(ForbiddenOperationException.class,
                () -> service.createMsmeRepresentative(createRequest()));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void createRepresentativeRejectsExistingRepresentative() {
        login(10L, "RM", null);
        existingMsme();

        when(userRepository.findByMsmeId(100L))
                .thenReturn(Optional.of(representative));

        assertThrows(DuplicateResourceException.class,
                () -> service.createMsmeRepresentative(createRequest()));

        verify(userRepository, never()).save(any(AppUser.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createRepresentativeRejectsDuplicateEmail() {
        login(10L, "RM", null);
        existingMsme();

        when(userRepository.existsByEmail("new@example.com"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.createMsmeRepresentative(createRequest()));

        verify(userRepository, never()).save(any(AppUser.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void assignedRmCanReadRepresentative() {
        login(10L, "RM", null);
        existingRepresentative();
        existingMsme();

        assertEquals(20L,
                service.getMsmeRepresentative(20L).getUserId());
    }

    @Test
    void anotherRmCannotReadRepresentative() {
        login(99L, "RM", null);
        existingRepresentative();
        existingMsme();

        assertThrows(ForbiddenOperationException.class,
                () -> service.getMsmeRepresentative(20L));
    }

    @Test
    void riskAdminCanReadRepresentative() {
        login(30L, "RISK_ADMIN", null);
        existingRepresentative();

        assertEquals(20L,
                service.getMsmeRepresentative(20L).getUserId());

        verifyNoInteractions(msmeRepository);
    }

    @Test
    void getRepresentativeRejectsNonRepresentativeUser() {
        login(30L, "RISK_ADMIN", null);
        representative.setRole("RM");
        existingRepresentative();

        assertThrows(ResourceNotFoundException.class,
                () -> service.getMsmeRepresentative(20L));
    }

    @Test
    void updateRepresentativeChangesDetailsAndPreservesAccountFields() {
        login(10L, "RM", null);
        existingRepresentative();
        existingMsme();

        LocalDateTime originalCreatedAt = representative.getCreatedAt();

        when(userRepository.save(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppUserResponse result =
                service.updateMsmeRepresentative(20L, updateRequest());

        assertAll(
                () -> assertEquals("Updated Representative", result.getName()),
                () -> assertEquals("updated@example.com", result.getEmail()),
                () -> assertEquals("MSME_REP", result.getRole()),
                () -> assertEquals(100L, result.getMsmeId()),
                () -> assertEquals("ACTIVE", result.getAccountStatus()),
                () -> assertEquals(originalCreatedAt, result.getCreatedAt()),
                () -> assertEquals("existing-hash",
                        representative.getPasswordHash())
        );

        verify(userRepository).save(representative);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateRepresentativeRejectsEmailUsedByAnotherAccount() {
        login(10L, "RM", null);
        existingRepresentative();
        existingMsme();

        AppUser otherUser = new AppUser();
        otherUser.setUserId(99L);

        when(userRepository.findByEmail("updated@example.com"))
                .thenReturn(Optional.of(otherUser));

        assertThrows(DuplicateResourceException.class,
                () -> service.updateMsmeRepresentative(
                        20L, updateRequest()));

        verify(userRepository, never()).save(any(AppUser.class));
        assertEquals("rep@example.com", representative.getEmail());
    }

    @Test
    void updateRepresentativeRejectsAnotherRmsAccess() {
        login(99L, "RM", null);
        existingRepresentative();
        existingMsme();

        assertThrows(ForbiddenOperationException.class,
                () -> service.updateMsmeRepresentative(
                        20L, updateRequest()));

        verify(userRepository, never()).save(any(AppUser.class));
    }

    private void existingMsme() {
        when(msmeRepository.findById(100L))
                .thenReturn(Optional.of(msme));
    }

    private void existingRepresentative() {
        when(userRepository.findById(20L))
                .thenReturn(Optional.of(representative));
    }

    private CreateMsmeRepRequest createRequest() {
        CreateMsmeRepRequest request = new CreateMsmeRepRequest();
        request.setName(" New Representative ");
        request.setEmail(" new@example.com ");
        request.setPassword("Password123!");
        request.setMsmeId(100L);
        return request;
    }

    private UpdateMsmeRepRequest updateRequest() {
        UpdateMsmeRepRequest request = new UpdateMsmeRepRequest();
        request.setName(" Updated Representative ");
        request.setEmail(" updated@example.com ");
        return request;
    }

    private void login(Long userId, String role, Long msmeId) {
        AppUser user = new AppUser();
        user.setUserId(userId);
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        user.setMsmeId(msmeId);
        user.setAccountStatus("ACTIVE");

        CustomUserPrincipal principal = new CustomUserPrincipal(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                )
        );
    }
}