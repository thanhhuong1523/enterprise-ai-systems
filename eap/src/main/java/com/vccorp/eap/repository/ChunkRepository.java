package com.vccorp.eap.repository;

import com.vccorp.eap.model.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, UUID>, ChunkRepositoryCustom {
}
