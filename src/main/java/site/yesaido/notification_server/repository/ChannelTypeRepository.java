package site.yesaido.notification_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.entity.ChannelType;

import java.util.Optional;

public interface ChannelTypeRepository extends JpaRepository<ChannelType, Long> {

    Optional<ChannelType> findByIdAndDeletedFalse(Long id);

    Optional<ChannelType> findByCodeAndDeletedFalse(String code);

    Optional<ChannelType> findByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByCodeAndDeletedFalseAndIdNot(String code, Long id);
}
