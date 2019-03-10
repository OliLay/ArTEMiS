package de.tum.in.www1.artemis.web.rest;

import java.net.URISyntaxException;
import java.security.Principal;
import java.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing ModelingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ModelingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionResource.class);

    private static final String ENTITY_NAME = "modelingSubmission";

    private final ModelingSubmissionRepository modelingSubmissionRepository;
    private final ResultRepository resultRepository;
    private final ModelingSubmissionService modelingSubmissionService;
    private final ModelingExerciseService modelingExerciseService;
    private final ParticipationService participationService;
    //    private final ExerciseService exerciseService;
    private final UserService userService;
    private final CourseService courseService;
    private final AuthorizationCheckService authCheckService;
    private final CompassService compassService;


    public ModelingSubmissionResource(ModelingSubmissionRepository modelingSubmissionRepository,
                                      ResultRepository resultRepository,
                                      ModelingSubmissionService modelingSubmissionService,
                                      ModelingExerciseService modelingExerciseService,
                                      ParticipationService participationService,
                                      ExerciseService exerciseService,
                                      UserService userService,
                                      CourseService courseService,
                                      AuthorizationCheckService authCheckService,
                                      CompassService compassService) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingExerciseService = modelingExerciseService;
        this.participationService = participationService;
//        this.exerciseService = exerciseService;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.compassService = compassService;
    }


    /**
     * POST  /courses/{courseId}/exercises/{exerciseId}/modeling-submissions : Create a new modelingSubmission.
     * This is called when a student saves his model the first time after starting the exercise or starting a retry.
     *
     * @param courseId           only included for API consistency, not actually used
     * @param exerciseId         the id of the exercise for which to init a participation
     * @param principal          the current user principal
     * @param modelingSubmission the modelingSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/courses/{courseId}/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    //TODO MJ return 201 CREATED with location header instead of ModelingSubmission
    public ResponseEntity<ModelingSubmission> createModelingSubmission(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to create ModelingSubmission : {}", modelingSubmission.getModel());
        if (modelingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new modelingSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }

        return handleModelingSubmission(exerciseId, principal, modelingSubmission);
    }


    /**
     * PUT  /courses/{courseId}/exercises/{exerciseId}/modeling-submissions : Updates an existing modelingSubmission.
     * This function is called by the modeling editor for saving and submitting modeling submissions.
     * The submit specific handling occurs in the ModelingSubmissionService.save() function.
     *
     * @param courseId           only included for API consistency, not actually used
     * @param exerciseId         the id of the exercise for which to init a participation
     * @param principal          the current user principal
     * @param modelingSubmission the modelingSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingSubmission,
     * or with status 400 (Bad Request) if the modelingSubmission is not valid,
     * or with status 500 (Internal Server Error) if the modelingSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses/{courseId}/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<ModelingSubmission> updateModelingSubmission(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to update ModelingSubmission : {}", modelingSubmission.getModel());
        if (modelingSubmission.getId() == null) {
            return createModelingSubmission(courseId, exerciseId, principal, modelingSubmission);
        }

        return handleModelingSubmission(exerciseId, principal, modelingSubmission);
    }


    @NotNull
    private ResponseEntity<ModelingSubmission> handleModelingSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {//TODO MJ move to ModelingSubmissionService?
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        ResponseEntity<ModelingSubmission> responseFailure = checkExerciseValidity(modelingExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

        Participation participation = participationService.findOneByExerciseIdAndStudentLoginAnyState(modelingExercise.getId(), principal.getName());

        // update and save submission
        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, participation);
        hideDetails(modelingSubmission);
        return ResponseEntity.ok(modelingSubmission);
    }


    private void hideDetails(@RequestBody ModelingSubmission modelingSubmission) {
        //do not send old submissions or old results to the client
        if (modelingSubmission.getParticipation() != null) {
            modelingSubmission.getParticipation().setSubmissions(null);
            modelingSubmission.getParticipation().setResults(null);

            if (modelingSubmission.getParticipation().getExercise() != null && modelingSubmission.getParticipation().getExercise() instanceof ModelingExercise) {
                //make sure the solution is not sent to the client
                ModelingExercise modelingExerciseForClient = (ModelingExercise) modelingSubmission.getParticipation().getExercise();
                modelingExerciseForClient.setSampleSolutionExplanation(null);
                modelingExerciseForClient.setSampleSolutionModel(null);
            }
        }
    }


    @Nullable
    private ResponseEntity<ModelingSubmission> checkExerciseValidity(ModelingExercise modelingExercise) {
        if (modelingExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        Course course = modelingExercise.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return forbidden();
        }
        return null;
    }


    /**
     * GET  /modeling-submissions : get all the modelingSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of modelingSubmissions in body
     */
    @GetMapping(value = "/courses/{courseId}/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ModelingSubmission>> getAllModelingSubmissions(@PathVariable Long courseId,
                                                                              @PathVariable Long exerciseId,
                                                                              @RequestParam(defaultValue = "false") boolean submittedOnly) {
        log.debug("REST request to get all ModelingSubmissions");
        Exercise exercise = modelingExerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }
        List<ModelingSubmission> submissions = modelingSubmissionService.getModelingSubmissions(exerciseId, submittedOnly);
        return ResponseEntity.ok(submissions);
    }


    /**
     * GET  /modeling-submissions/{submissionId} : Gets an existing modelingSubmission with result. If no result
     * exists for this submission a new Result object is created and assigned to the submission.
     *
     * @param submissionId the id of the modelingSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the modelingSubmission for the given id,
     * or with status 404 (Not Found) if the modelingSubmission could not be found
     */
    @GetMapping("/modeling-submissions/{submissionId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<ModelingSubmission> getModelingSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get ModelingSubmission with id: {}", submissionId);
        ModelingSubmission modelingSubmission = modelingSubmissionService.findOne(submissionId);

        Exercise exercise = modelingSubmission.getParticipation().getExercise();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        Result result = modelingSubmission.getResult();
        if (result == null) {
            result = new Result();
            result.setSubmission(modelingSubmission);
            modelingSubmission.setResult(result);
            modelingSubmission.getParticipation().addResult(result);
            result = resultRepository.save(result);
            modelingSubmission = modelingSubmissionRepository.save(modelingSubmission);
        }
        if (result.getAssessor() == null) {
            compassService.removeModelWaitingForAssessment(exercise.getId(), submissionId);
            //we set the assessor and save the result to soft lock the assessment (so that it cannot be edited by another tutor)
            result.setAssessor(userService.getUserWithGroupsAndAuthorities());
            Result savedResult = resultRepository.save(result);
            log.debug("Assessment locked with result id: " + savedResult.getId() + " for assessor: " + savedResult.getAssessor().getFirstName());
        }

        //Make sure the exercise is connected to the participation in the json response
        modelingSubmission.getParticipation().setExercise(exercise);
        hideDetails(modelingSubmission);
        return ResponseEntity.ok(modelingSubmission);
    }
}
