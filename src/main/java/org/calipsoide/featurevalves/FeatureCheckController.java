package org.calipsoide.featurevalves;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * REST endpoint that evaluates feature checks on incoming requests.
 *
 * @see FeatureService
 * @see Feature#execute(FeatureCheck)
 */
@RestController
@RequiredArgsConstructor
public class FeatureCheckController {

    private final FeatureService featureService;

    /**
     * Evaluates a feature check for the given application and feature.
     * <p>
     * Returns {@code 200 OK} with the evaluation result when the feature is
     * known, or {@code 404 Not Found} when it is not.
     *
     * @param applicationCode the client application name
     * @param featureCode     the feature code
     * @param request         the request body carrying the tag data
     * @return a {@code Mono} of the response, empty-cased via
     *         {@link ResponseEntity#notFound()}
     */
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
