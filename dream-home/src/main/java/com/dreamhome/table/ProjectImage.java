package com.dreamhome.table;

import com.dreamhome.table.enumeration.ImageStatus;
import com.dreamhome.table.enumeration.ImageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Data
public class ProjectImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID engineerId;
    private UUID clientId;
    private UUID projectId;
    private UUID imageId;
    private String extension;
    @Enumerated(EnumType.STRING)
    private ImageStatus status = ImageStatus.PENDING;
    private String reason;
    @Enumerated(EnumType.STRING)
    private ImageType type;
    private String projectName;
    private String clientName;

    public ProjectImage(UUID engineerId, UUID clientId, UUID projectId, UUID imageId, ImageType type,String extension,String projectName,String clientName) {
        this.engineerId = engineerId;
        this.clientId = clientId;
        this.projectId = projectId;
        this.imageId = imageId;
        this.type = type;
        this.extension = extension;
        this.projectName = projectName;
        this.clientName = clientName;
    }
}
