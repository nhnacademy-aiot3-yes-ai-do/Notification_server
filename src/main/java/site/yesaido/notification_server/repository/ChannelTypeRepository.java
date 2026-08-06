package site.yesaido.notification_server.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.domain.ChannelType;

public interface ChannelTypeRepository extends JpaRepository<ChannelType, Long> {

    Optional<ChannelType> findByIdAndDeletedFalse(Long id);
}
