
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url,Integer>
{
    Optional<Url> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);
    boolean existsByLongUrl(String LongUrl);
    void deleteByShortCode(String shortCode);
}