package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static java.math.BigDecimal.ROUND_HALF_EVEN;

@Service
public class ModelingAssessmentService extends AssessmentService {
    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentService.class);

    private final UserService userService;
    private final ModelingSubmissionRepository modelingSubmissionRepository;
    private final FeedbackRepository feedbackRepository;

    public ModelingAssessmentService(ResultRepository resultRepository,
                                     UserService userService,
                                     ModelingSubmissionRepository modelingSubmissionRepository,
                                     FeedbackRepository feedbackRepository) {
        super(resultRepository);
        this.userService = userService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.feedbackRepository = feedbackRepository;
    }


    /**
     * This function is used for submitting a manual assessment/result. It updates the completion date, sets the
     * assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the result in the database.
     *
     * @param result   the result the assessment belongs to
     * @param exercise the exercise the assessment belongs to
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitManualAssessment(Result result, ModelingExercise exercise) {
        // TODO CZ: use AssessmentService#submitResult() function instead
        result.setRatedIfNotExceeded(exercise.getDueDate(), result.getSubmission().getSubmissionDate());
        result.setCompletionDate(ZonedDateTime.now());
        result.evaluateFeedback(exercise.getMaxScore()); // TODO CZ: move to AssessmentService class, as it's the same for modeling and text exercises (i.e. total score is sum of feedback credits)
        resultRepository.save(result);
        return result;
    }


    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to MANUAL
     * and sets the assessor attribute. Furthermore, it saves the result in the database.
     *
     * @param result the result the assessment belongs to
     */
    @Transactional
    public void saveManualAssessment(Result result) {
        result.setAssessmentType(AssessmentType.MANUAL);
        User user = userService.getUser();
        result.setAssessor(user);

        if (result.getSubmission() instanceof ModelingSubmission && result.getSubmission().getResult() == null) {
            ModelingSubmission modelingSubmission = (ModelingSubmission) result.getSubmission();
            modelingSubmission.setResult(result);
            modelingSubmissionRepository.save(modelingSubmission);
        }
        resultRepository.save(result);
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to MANUAL
     * and sets the assessor attribute. Furthermore, it saves the result in the database.
     *
     * @param modelingSubmission
     * @param feedbacks
     */
    @Transactional
    public Result saveManualAssessment(ModelingSubmission modelingSubmission, List<Feedback> feedbacks) {
        Result result = modelingSubmission.getResult();
        if (result == null) {
            result = new Result();
        }

        result.setAssessmentType(AssessmentType.MANUAL);
        User user = userService.getUser();
        result.setAssessor(user);

        // delete old feedback from the database for this result
        feedbackRepository.deleteByResult(result);
        result.getFeedbacks().clear();
        for (Feedback feedback : feedbacks) {
            feedback.setType(FeedbackType.MANUAL);
            result.addFeedback(feedback);
        }
        feedbackRepository.saveAll(feedbacks);
        result.setHasFeedback(true);

        if (result.getSubmission() == null) {
            result.setSubmission(modelingSubmission);
            // TODO CZ: is setting the result and saving the submission really necessary here? setting the submission of the result should be enough as the relationship is owned by the result
            modelingSubmission.setResult(result);
            modelingSubmissionRepository.save(modelingSubmission);
        }
        return resultRepository.save(result);
    }


    /**
     * TODO CZ: I added this function again due to a compile error in the ModelingAssessmentServiceTest
     *
     * @return sum of every modelingAssessments credit rounded to max two numbers after the comma
     */
    public static Double calculateTotalScore(List<ModelElementAssessment> modelingAssessment) {
        double totalScore = 0.0;
        for (ModelElementAssessment assessment : modelingAssessment) {
            totalScore += assessment.getCredits();
        }
        return new BigDecimal(totalScore).setScale(2, ROUND_HALF_EVEN).doubleValue();
    }
}
