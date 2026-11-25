package com.oldmermaidman.gomoku.backend.legal;

import com.oldmermaidman.gomoku.backend.testsupport.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two pages Google Play links to (PLAY_RELEASE.md).
 *
 * <p>Both are open to anonymous visitors — a reviewer follows the store listing link
 * without signing in — and both must name what actually happens to the data.
 */
class LegalPagesTest extends ApiTestSupport {

    @Test
    void thePrivacyPolicyIsPublic() throws Exception {
        Response privacy = get("/legal/privacy", null);

        assertThat(privacy.status()).isEqualTo(200);
        assertThat(privacy.body()).contains("Политика конфиденциальности");
        // The facts the Play data-safety form repeats have to be on the page.
        assertThat(privacy.body()).contains("bcrypt");
        assertThat(privacy.body()).contains("30 дней");
        assertThat(privacy.body()).contains("Summary in English");
    }

    @Test
    void theDeletionPageIsPublicAndDescribesTheRealEffect() throws Exception {
        Response deletion = get("/legal/delete-account", null);

        assertThat(deletion.status()).isEqualTo(200);
        assertThat(deletion.body()).contains("Удаление аккаунта");
        // Deletion anonymises rather than erasing rows: the page says so.
        assertThat(deletion.body()).contains("del_");
        assertThat(deletion.body()).contains("обезлич");
        assertThat(deletion.body()).contains("Account deletion in English");
    }

    @Test
    void theLegalRootLeadsToThePolicy() throws Exception {
        Response root = get("/legal", null);

        assertThat(root.status()).isEqualTo(302);
        assertThat(root.header("Location")).endsWith("/legal/privacy");
    }
}