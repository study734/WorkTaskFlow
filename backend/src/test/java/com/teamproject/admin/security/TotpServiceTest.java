package com.teamproject.admin.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {
    @Test
    void followsRfc6238Sha1CounterVectorWithSixDigits() {
        TotpService service = new TotpService();
        assertThat(service.generate("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 1L)).isEqualTo("287082");
    }
}
