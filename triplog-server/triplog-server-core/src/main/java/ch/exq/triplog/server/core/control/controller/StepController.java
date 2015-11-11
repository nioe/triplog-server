package ch.exq.triplog.server.core.control.controller;

import ch.exq.triplog.server.common.comparator.StepFromDateComparator;
import ch.exq.triplog.server.common.dto.Picture;
import ch.exq.triplog.server.common.dto.Step;
import ch.exq.triplog.server.common.dto.StepDetail;
import ch.exq.triplog.server.common.dto.StepMin;
import ch.exq.triplog.server.core.control.exceptions.DisplayableException;
import ch.exq.triplog.server.core.entity.dao.StepDAO;
import ch.exq.triplog.server.core.entity.dao.TripDAO;
import ch.exq.triplog.server.core.entity.db.PictureDBObject;
import ch.exq.triplog.server.core.entity.db.StepDBObject;
import ch.exq.triplog.server.core.mapper.TriplogMapper;
import ch.exq.triplog.server.util.id.IdGenerator;
import com.mongodb.WriteResult;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * User: Nicolas Oeschger <noe@exq.ch>
 * Date: 25.04.14
 * Time: 15:58
 */
@Stateless
public class StepController {

    @Inject
    Logger logger;

    @Inject
    TripDAO tripDAO;

    @Inject
    StepDAO stepDAO;

    @Inject
    ResourceController resourceController;

    @Inject
    TriplogMapper mapper;


    public List<Step> getAllStepsOfTrip(String tripId) {
        if (tripDAO.getTripById(tripId) == null) {
            return null;
        }

        return stepDAO.getAllStepsOfTrip(tripId).stream().map(stepDBObject -> mapper.map(stepDBObject, Step.class))
                .collect(toList());
    }

    public StepDetail getStep(String tripId, String stepId) {
        StepDBObject stepDBObject = stepDAO.getStep(tripId, stepId);
        StepDetail stepDetail = null;

        if (stepDBObject != null) {
            stepDetail = mapper.map(stepDBObject, StepDetail.class);
            findPreviousAndNext(stepDetail);
        }

        return stepDetail;
    }

    public StepDetail createStep(final StepDetail stepDetail) throws DisplayableException {
        if (stepDetail.getTripId() == null || tripDAO.getTripById(stepDetail.getTripId()) == null) {
            throw new DisplayableException("Could not find trip with id " + stepDetail.getTripId());
        }

        if (stepDetail.getStepName() == null || stepDetail.getStepName().isEmpty()) {
            throw new DisplayableException("Step incomplete: stepName must be set");
        }

        if (stepDetail.getFromDate() == null || stepDetail.getToDate() == null) {
            throw new DisplayableException("Step incomplete: fromDate and toDate must be set");
        }

        stepDetail.setStepId(IdGenerator.generateIdWithFullDate(stepDetail.getStepName(), stepDetail.getFromDate()));

        StepDBObject stepDBObject = mapper.map(stepDetail, StepDBObject.class);
        checkFromDateIsBeforeOrEqualsToDate(stepDBObject);

        //We never add images directly
        stepDBObject.setCoverPicture(null);
        stepDBObject.setPictures(null);

        stepDAO.createStep(stepDBObject);

        return mapper.map(stepDBObject, StepDetail.class);
    }

    public StepDetail updateStep(String tripId, String stepId, StepDetail stepDetail) throws DisplayableException {
        StepDBObject currentStep = getStepOrThrowException(tripId, stepId);
        StepDBObject changedStep = mapper.map(stepDetail, StepDBObject.class);

        //We never change ids like this
        changedStep.setStepId(null);
        changedStep.setTripId(null);

        List<PictureDBObject> pictures = updatePictures(currentStep, changedStep);

        try {
            currentStep.updateFrom(changedStep);
        } catch (InvocationTargetException | IllegalAccessException e) {
            String message = "Step could not be updated";
            logger.warn(message, e);
            throw new DisplayableException(message, e);
        }

        currentStep.setPictures(pictures);

        checkFromDateIsBeforeOrEqualsToDate(currentStep);
        stepDAO.updateStep(tripId, stepId, currentStep);

        return mapper.map(currentStep, StepDetail.class);
    }

    public StepDetail addPicture(String tripId, String stepId, Picture picture) throws DisplayableException {
        StepDBObject step = getStepOrThrowException(tripId, stepId);

        List<PictureDBObject> updatedPictures = step.getPictures();
        updatedPictures.add(mapper.map(picture, PictureDBObject.class));
        step.setPictures(updatedPictures);
        stepDAO.updateStep(tripId, stepId, step);

        return mapper.map(step, StepDetail.class);
    }

    public StepDetail deletePicture(String tripId, String stepId, String pictureName) throws DisplayableException {
        StepDBObject step = getStepOrThrowException(tripId, stepId);

        List<PictureDBObject> pictureToDelete = step.getPictures().stream().filter(picture -> picture.getName().equals(pictureName)).collect(toList());
        if (pictureToDelete.size() > 1) {
            throw new IllegalArgumentException("More than one picture with ID " + pictureName + " found on step " + stepId + " of trip " + tripId);
        } else if (pictureToDelete.size() == 1) {
            List<PictureDBObject> updatedPictures = step.getPictures();
            updatedPictures.remove(pictureToDelete.get(0));
            step.setPictures(updatedPictures);
            stepDAO.updateStep(tripId, stepId, step);
        } else {
           throw new DisplayableException("No picture with ID " + pictureName + " found on step " + stepId + " of trip " + tripId);
        }

        return mapper.map(step, StepDetail.class);
    }

    public boolean deleteStep(String tripId, String stepId) {
        StepDBObject stepDBObject = stepDAO.getStep(tripId, stepId);
        if (stepDBObject == null) {
            return false;
        }

        WriteResult result = stepDAO.deleteStep(stepDBObject);
        return result.getN() == 1 && result.getError() == null;
    }

    public boolean deleteAllStepsOfTrip(String tripId) {
        WriteResult result = stepDAO.deleteAllStepsOfTrip(tripId);
        return result.getN() > 0 && result.getError() == null;
    }

    private void findPreviousAndNext(StepDetail stepDetail) {
        List<Step> allStepsOfTrip = getAllStepsOfTrip(stepDetail.getTripId());
        allStepsOfTrip.sort(new StepFromDateComparator());

        int index = allStepsOfTrip.indexOf(stepDetail);
        int prevIndex = index - 1;
        int nextIndex = index + 1;

        stepDetail.setPreviousStep(prevIndex >= 0 && prevIndex < allStepsOfTrip.size() ? new StepMin(allStepsOfTrip.get(prevIndex)) : null);
        stepDetail.setNextStep(nextIndex >= 0 && nextIndex < allStepsOfTrip.size() ? new StepMin(allStepsOfTrip.get(nextIndex)) : null);
    }

    private void checkFromDateIsBeforeOrEqualsToDate(StepDBObject stepDBObject) throws DisplayableException {
        if (!(stepDBObject.getFromDate().isBefore(stepDBObject.getToDate()) || stepDBObject.getFromDate().isEqual(stepDBObject.getToDate()))) {
            throw new DisplayableException("FromDate has to be before or equal toDate");
        }
    }

    private StepDBObject getStepOrThrowException(String tripId, String stepId) throws DisplayableException {
        StepDBObject currentStep = stepDAO.getStep(tripId, stepId);
        if (currentStep == null) {
            throw new DisplayableException("Step " + stepId + " could not be found");
        }

        return currentStep;
    }

    private List<PictureDBObject> updatePictures(StepDBObject currentStep, StepDBObject changedStep) throws DisplayableException {
        List<PictureDBObject> changedPictures = changedStep.getPictures();
        if (changedPictures != null) {
            List<PictureDBObject> pictures = new ArrayList<>();

            for (PictureDBObject currentPicture : currentStep.getPictures()) {
                List<PictureDBObject> changedPicture = changedPictures.stream().filter(candidate -> candidate.getName().equals(currentPicture.getName())).collect(toList());
                if (changedPicture.size() > 1) {
                    throw new DisplayableException("More than one picture with name " + currentPicture.getName() + " found.");
                } else if (changedPicture.size() == 1) {
                    PictureDBObject picture = changedPicture.get(0);
                    pictures.add(new PictureDBObject(currentPicture.getName(), currentPicture.getLocation(), picture.getCaption(), picture.isShownInGallery()));
                }
            }

            return pictures;
        }

        return new ArrayList<>(currentStep.getPictures());
    }
}
