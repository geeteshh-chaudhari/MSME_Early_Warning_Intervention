package com.msme.service;

import com.msme.dto.msme.CreateMsmeRequest;
import com.msme.dto.msme.MsmeResponse;
import com.msme.dto.msme.UpdateMsmeRequest;
import com.msme.entity.AppUser;
import com.msme.entity.Msme;
import com.msme.exception.ForbiddenOperationException;
import com.msme.exception.InvalidRequestException;
import com.msme.exception.ResourceNotFoundException;
import com.msme.repository.AppUserRepository;
import com.msme.repository.MsmeRepository;
import com.msme.security.CustomUserPrincipal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MsmeServiceTest {

    @Mock
    private MsmeRepository msmeRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private MsmeService service;

    private Msme msme;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        msme = new Msme();
        msme.setMsmeId(100L);
        msme.setBusinessName("ABC Industries");
        msme.setSector("Manufacturing");
        msme.setContactName("Neha");
        msme.setSanctionedCreditLimit(new BigDecimal("500000.00"));
        msme.setConsentStatus("GRANTED");
        msme.setAssignedRmId(10L);
        msme.setIsActive("Y");
        msme.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        msme.setUpdatedAt(msme.getCreatedAt());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createMsmeAssignsLoggedInRmAndTrimsFields() {
        login(10L, "RM", null);

        CreateMsmeRequest request = new CreateMsmeRequest(
                " ABC Industries ",
                " Manufacturing ",
                " Neha ",
                new BigDecimal("500000.00"),
                " GRANTED "
        );

        when(msmeRepository.save(any(Msme.class))).thenAnswer(invocation -> {
            Msme saved = invocation.getArgument(0);
            saved.setMsmeId(100L);
            return saved;
        });

        MsmeResponse result = service.createMsme(request);

        assertAll(
                () -> assertEquals(100L, result.getMsmeId()),
                () -> assertEquals("ABC Industries", result.getBusinessName()),
                () -> assertEquals("Manufacturing", result.getSector()),
                () -> assertEquals("Neha", result.getContactName()),
                () -> assertEquals(request.getSanctionedCreditLimit(),
                        result.getSanctionedCreditLimit()),
                () -> assertEquals("GRANTED", result.getConsentStatus()),
                () -> assertEquals(10L, result.getAssignedRmId()),
                () -> assertEquals("Y", result.getIsActive()),
                () -> assertNotNull(result.getCreatedAt()),
                () -> assertEquals(result.getCreatedAt(), result.getUpdatedAt())
        );

        verify(msmeRepository).save(any(Msme.class));
    }

    @Test
    void createMsmeAllowsMissingConsentStatus() {
        login(10L, "RM", null);

        CreateMsmeRequest request = new CreateMsmeRequest(
                "ABC Industries", "Manufacturing", "Neha",
                new BigDecimal("500000.00"), null
        );

        when(msmeRepository.save(any(Msme.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertNull(service.createMsme(request).getConsentStatus());
    }

    @Test
    void createMsmeRejectsMissingAuthentication() {
        CreateMsmeRequest request = new CreateMsmeRequest(
                "ABC Industries", "Manufacturing", "Neha",
                new BigDecimal("500000.00"), null
        );

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> service.createMsme(request));

        verifyNoInteractions(msmeRepository, appUserRepository);
    }

    @Test
    void getAllMsmesReturnsMappedResults() {
        when(msmeRepository.findAll()).thenReturn(List.of(msme));

        List<MsmeResponse> results = service.getAllMsmes();

        assertEquals(1, results.size());
        assertEquals(100L, results.get(0).getMsmeId());
        assertEquals("ABC Industries", results.get(0).getBusinessName());
    }

    @Test
    void getMsmesByRmReturnsAssignedMsmes() {
        AppUser rm = new AppUser();
        rm.setRole("RM");

        when(appUserRepository.findById(10L)).thenReturn(Optional.of(rm));
        when(msmeRepository.findByAssignedRmId(10L))
                .thenReturn(List.of(msme));

        assertEquals(100L,
                service.getMsmesByRm(10L).get(0).getMsmeId());
    }

    @Test
    void getMsmesByRmRejectsMissingUser() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getMsmesByRm(99L));

        verifyNoInteractions(msmeRepository);
    }

    @Test
    void getMsmesByRmRejectsUserWhoIsNotRm() {
        AppUser representative = new AppUser();
        representative.setRole("MSME_REP");

        when(appUserRepository.findById(20L))
                .thenReturn(Optional.of(representative));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getMsmesByRm(20L));

        verifyNoInteractions(msmeRepository);
    }

    @Test
    void getMyMsmesUsesLoggedInRmId() {
        login(10L, "RM", null);
        when(msmeRepository.findByAssignedRmId(10L))
                .thenReturn(List.of(msme));

        assertEquals(100L, service.getMyMsmes().get(0).getMsmeId());

        verify(msmeRepository).findByAssignedRmId(10L);
    }

    @Test
    void representativeCanGetOwnMsme() {
        login(20L, "MSME_REP", 100L);
        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));

        assertEquals(100L, service.getMyMsme().getMsmeId());
    }

    @Test
    void representativeWithoutMsmeIsRejected() {
        login(20L, "MSME_REP", null);

        assertThrows(ForbiddenOperationException.class,
                () -> service.getMyMsme());

        verifyNoInteractions(msmeRepository);
    }

    @Test
    void riskAdminCanReadAnyMsme() {
        login(30L, "RISK_ADMIN", null);
        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));

        assertEquals(100L, service.getMsmeById(100L).getMsmeId());
    }

    @Test
    void assignedRmCanReadMsme() {
        login(10L, "RM", null);
        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));

        assertEquals(100L, service.getMsmeById(100L).getMsmeId());
    }

    @Test
    void anotherRmCannotReadMsme() {
        login(99L, "RM", null);
        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));

        assertThrows(ForbiddenOperationException.class,
                () -> service.getMsmeById(100L));
    }

    @Test
    void representativeCannotReadAnotherMsme() {
        login(20L, "MSME_REP", 200L);
        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));

        assertThrows(ForbiddenOperationException.class,
                () -> service.getMsmeById(100L));
    }

    @Test
    void getMissingMsmeThrowsNotFound() {
        login(10L, "RM", null);
        when(msmeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getMsmeById(999L));
    }

    @Test
    void updateOwnMsmeChangesFieldsAndPreservesOwnership() {
        login(10L, "RM", null);
        LocalDateTime originalCreatedAt = msme.getCreatedAt();

        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));
        when(msmeRepository.save(any(Msme.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MsmeResponse result = service.updateMsme(100L, updateRequest());

        assertAll(
                () -> assertEquals("Updated Business", result.getBusinessName()),
                () -> assertEquals("Services", result.getSector()),
                () -> assertEquals("Updated Contact", result.getContactName()),
                () -> assertEquals(new BigDecimal("750000.00"),
                        result.getSanctionedCreditLimit()),
                () -> assertNull(result.getConsentStatus()),
                () -> assertEquals(10L, result.getAssignedRmId()),
                () -> assertEquals("Y", result.getIsActive()),
                () -> assertEquals(originalCreatedAt, result.getCreatedAt())
        );

        verify(msmeRepository).save(msme);
    }

    @Test
    void updateRejectsAnotherRmsMsme() {
        login(99L, "RM", null);
        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));

        assertThrows(ForbiddenOperationException.class,
                () -> service.updateMsme(100L, updateRequest()));

        verify(msmeRepository, never()).save(any(Msme.class));
    }

    @Test
    void updateRejectsInactiveMsme() {
        login(10L, "RM", null);
        msme.setIsActive("N");
        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));

        assertThrows(InvalidRequestException.class,
                () -> service.updateMsme(100L, updateRequest()));

        verify(msmeRepository, never()).save(any(Msme.class));
    }

    @Test
    void deactivateAlsoDisablesRepresentativeAccount() {
        login(10L, "RM", null);

        AppUser representative = new AppUser();
        representative.setAccountStatus("ACTIVE");

        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));
        when(appUserRepository.findByMsmeId(100L))
                .thenReturn(Optional.of(representative));
        when(msmeRepository.save(any(Msme.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MsmeResponse result = service.deactivateMsme(100L);

        assertEquals("N", result.getIsActive());
        assertEquals("INACTIVE", representative.getAccountStatus());
        assertNotNull(representative.getUpdatedAt());

        verify(appUserRepository).save(representative);
        verify(msmeRepository).save(msme);
    }

    @Test
    void deactivateWorksWithoutRepresentative() {
        login(10L, "RM", null);

        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));
        when(appUserRepository.findByMsmeId(100L))
                .thenReturn(Optional.empty());
        when(msmeRepository.save(any(Msme.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("N", service.deactivateMsme(100L).getIsActive());

        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void deactivateRejectsAnotherRmsMsme() {
        login(99L, "RM", null);
        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));

        assertThrows(ForbiddenOperationException.class,
                () -> service.deactivateMsme(100L));

        verify(msmeRepository, never()).save(any(Msme.class));
        verifyNoInteractions(appUserRepository);
    }

    @Test
    void deactivateRejectsAlreadyInactiveMsme() {
        login(10L, "RM", null);
        msme.setIsActive("N");
        when(msmeRepository.findById(100L)).thenReturn(Optional.of(msme));

        assertThrows(InvalidRequestException.class,
                () -> service.deactivateMsme(100L));

        verify(msmeRepository, never()).save(any(Msme.class));
        verifyNoInteractions(appUserRepository);
    }

    private UpdateMsmeRequest updateRequest() {
        return new UpdateMsmeRequest(
                " Updated Business ",
                " Services ",
                " Updated Contact ",
                new BigDecimal("750000.00"),
                null
        );
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