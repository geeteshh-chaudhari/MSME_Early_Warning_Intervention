package com.msme;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.dao.DataAccessResourceFailureException;
import jakarta.servlet.http.Cookie;
import com.msme.controller.AuthController;
import com.msme.service.AuthService;
import com.msme.dto.auth.*;
import com.msme.security.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
class BrowserAuthenticationTest {
    @Test void browserLoginUsesHttpOnlyCookieAndDoesNotReturnTokenToJavascript() {
        var auth=mock(AuthService.class);
        when(auth.login(any())).thenReturn(new LoginResponse("signed-token","Bearer",7L,"RM","rm@example.test","RM",null));
        var controller=new AuthController(auth,3600000,false);
        var result=controller.login(new LoginRequest(),"cookie",new MockHttpServletRequest());
        assertNull(result.getBody().getAccessToken());
        var cookies=result.getHeaders().get("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(c->c.contains("MSME_AUTH=signed-token") && c.contains("HttpOnly") && c.contains("SameSite=Strict")));
        assertTrue(cookies.stream().anyMatch(c->c.startsWith("MSME_CSRF=") && !c.contains("HttpOnly")));
    }
    @Test void logoutExpiresBothCookies() {
        var result=new AuthController(mock(AuthService.class),3600000,false).logout(new MockHttpServletRequest());
        assertEquals(2,result.getHeaders().get("Set-Cookie").size());
        assertTrue(result.getHeaders().get("Set-Cookie").stream().allMatch(c->c.contains("Max-Age=0")));
    }
    @Test void authenticationDatabaseFailureReturns503() throws Exception {
        var jwt=mock(JwtService.class);var users=mock(CustomUserDetailsService.class);
        when(jwt.extractUserId("token")).thenReturn(7L);
        when(users.loadUserById(7L)).thenThrow(new DataAccessResourceFailureException("private database details"));
        var request=new MockHttpServletRequest("GET","/api/msmes/my");request.addHeader("Authorization","Bearer token");
        var response=new MockHttpServletResponse();
        new JwtAuthenticationFilter(jwt,users).doFilter(request,response,new MockFilterChain());
        assertEquals(503,response.getStatus());assertFalse(response.getErrorMessage().contains("private"));
    }
    @Test void directCookieWritesRequireCsrf() {
        var request=new MockHttpServletRequest("POST","/api/snapshots");
        request.setCookies(new Cookie("MSME_AUTH","token"),new Cookie("MSME_CSRF","expected"));
        assertFalse(BrowserAuthentication.csrfValid(request));
        request.addHeader("X-CSRF-Token","expected");assertTrue(BrowserAuthentication.csrfValid(request));
    }
    @Test void representativeOfInactiveBusinessCannotAuthenticateEvenIfAccountStillActive() {
        var users=mock(com.msme.repository.AppUserRepository.class);
        var businesses=mock(com.msme.repository.MsmeRepository.class);
        var user=new com.msme.entity.AppUser();user.setUserId(8L);user.setRole("MSME_REP");user.setMsmeId(10L);user.setAccountStatus("ACTIVE");
        var business=new com.msme.entity.Msme();business.setMsmeId(10L);business.setIsActive("N");
        when(users.findById(8L)).thenReturn(java.util.Optional.of(user));
        when(businesses.findById(10L)).thenReturn(java.util.Optional.of(business));
        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                ()->new CustomUserDetailsService(users,businesses).loadUserById(8L));
    }
}
