package com.carechain.triage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TriageModelRetrainRequest {

    @Size(max = 80, message = "Model version must be 80 characters or fewer")
    private String modelVersion;

    @Size(max = 120, message = "Corpus label must be 120 characters or fewer")
    private String corpusLabel;

    @Valid
    private List<TriageTrainingExampleRequest> examples = new ArrayList<>();
}
