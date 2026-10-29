package cn.labmanage;

import cn.labmanage.common.BusinessException;
import cn.labmanage.security.AccessService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessServiceTest {
    @Test void systemAdminBypassesLabScope() {
        JdbcTemplate db=mock(JdbcTemplate.class); AccessService service=new AccessService(db);
        var auth=new UsernamePasswordAuthenticationToken("admin",null,List.of(new SimpleGrantedAuthority("ROLE_SYS_ADMIN")));
        assertDoesNotThrow(() -> service.requireLab(auth,99)); verifyNoInteractions(db);
    }
    @Test void labAdminCannotAccessUnassignedLab() {
        JdbcTemplate db=mock(JdbcTemplate.class); when(db.queryForObject(anyString(),eq(Integer.class),eq("labadmin"),eq(2L))).thenReturn(0);
        AccessService service=new AccessService(db); var auth=new UsernamePasswordAuthenticationToken("labadmin",null,List.of(new SimpleGrantedAuthority("ROLE_LAB_ADMIN")));
        assertThrows(BusinessException.class,() -> service.requireLab(auth,2));
    }
}
