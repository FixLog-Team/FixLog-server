package com.fixlog.application.repository;

import com.fixlog.domain.model.LabelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabelRepository extends JpaRepository<LabelEntity, UUID> {

    List<LabelEntity> findByWorkspaceIdOrderByLabelNameAsc(UUID workspaceId);

    Optional<LabelEntity> findByWorkspaceIdAndLabelName(UUID workspaceId, String labelName);

    /** 라벨은 워크스페이스를 넘지 않는다. ID만으로 찾지 않는다. */
    Optional<LabelEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
