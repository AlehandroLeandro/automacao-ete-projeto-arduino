package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

// SPEC_DEVIATION: assertOwned recebe a mensagem de "não encontrado" do domínio (3 parametros),
// não os 2 do design.md.
// Reason: cada domínio usa uma mensagem própria ("Flow measurement not found", "pH measurement not
// found", "Lime record not found") e a resposta 404 precisa ser indistinguível da de um id
// inexistente (spec P1 AC5 e Edge Cases).
@Component
public class OperatorAuthorshipPolicy {

    public Optional<UUID> authorFilter(UserPrincipal principal) {
        if (principal == null || principal.getRole() != UserRole.OPERATOR) {
            return Optional.empty();
        }
        return Optional.of(principal.getId());
    }

    public UUID authorFilterOrNull(UserPrincipal principal) {
        return authorFilter(principal).orElse(null);
    }

    public void assertOwned(UserPrincipal principal, AppUser createdBy, String notFoundMessage) {
        Optional<UUID> author = authorFilter(principal);
        if (author.isEmpty()) {
            return;
        }
        if (createdBy == null || !author.get().equals(createdBy.getId())) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
    }
}
