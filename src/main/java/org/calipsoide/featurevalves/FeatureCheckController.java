package org.calipsoide.featurevalves;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Created by epalumbo on 9/16/17.
 */
@RestController
public class FeatureCheckController {

    private FeatureService featureService;

    @Autowired
    public FeatureCheckController(CachingFeatureService featureService) {
        this.featureService = featureService;
    }

    @PostMapping("/feature_valves/{application}/{feature}/checks")
    public Mono<ResponseEntity<FeatureCheckResponse>> check(
            @PathVariable("application") String applicationCode,
            @PathVariable("feature") String featureCode,
            @RequestBody FeatureCheckRequest request) {
        final ClientApplicationId applicationId = ClientApplicationId.of(applicationCode);
        final FeatureId id = new FeatureId(applicationId, featureCode);
        return featureService
                .findBy(id)
                .map(feature -> {
                    final List<Tag> tags =
                            request.tags().entrySet().stream()
                                    .map(entry -> new Tag(entry.getKey(), entry.getValue()))
                                    .collect(toList());
                    final FeatureCheck check = new FeatureCheck(tags);
                    final boolean result = feature.execute(check);
                    return new FeatureCheckResponse(result);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

}
