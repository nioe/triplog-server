package ch.exq.triplog.server.core.control.controller;

import ch.exq.triplog.server.common.dto.GpsPoint;
import ch.exq.triplog.server.common.dto.Trip;
import ch.exq.triplog.server.core.control.exceptions.DisplayableException;
import ch.exq.triplog.server.core.entity.dao.TripDAO;
import ch.exq.triplog.server.core.entity.db.TripDBObject;
import ch.exq.triplog.server.core.mapper.TriplogMapper;
import ch.exq.triplog.server.util.id.IdGenerator;
import com.mongodb.WriteResult;
import org.slf4j.Logger;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User: Nicolas Oeschger <noe@exq.ch>
 * Date: 25.04.14
 * Time: 15:58
 */
@Stateless
public class TripController {

    @Inject
    Logger logger;

    @Inject
    TripDAO tripDAO;

    @Inject
    StepController stepController;

    @Inject
    ResourceController resourceController;

    @Inject
    TriplogMapper mapper;

    public Trip getTripById(String tripId) {
        TripDBObject tripDBObject = tripDAO.getTripById(tripId);

        if (tripDBObject != null) {
            Trip trip = mapper.map(tripDBObject, Trip.class);
            addStepsToTrip(trip);

            return trip;
        }

        return null;
    }

    public List<Trip> getAllTrips() {
        List<Trip> allTrips = tripDAO.getAllTrips().stream().map(tripDBObject -> mapper.map(tripDBObject, Trip.class))
                                                            .collect(Collectors.toList());
        allTrips.forEach(this::addStepsToTrip);

        return allTrips;
    }

    public Trip createTrip(Trip trip) throws DisplayableException {
        if (trip == null || trip.getTripName() == null || trip.getTripName().isEmpty()) {
            throw new DisplayableException("Trip incomplete: At least tripName must be set");
        }

        String tripId = IdGenerator.generateIdWithYear(trip.getTripName(), trip.getTripDate());
        trip.setTripId(tripId);

        // We never update created and updated timestamps here
        trip.setCreated(null);
        trip.setLastUpdated(null);

        TripDBObject tripDBObject = mapper.map(trip, TripDBObject.class);
        tripDAO.createTrip(tripDBObject);

        return mapper.map(tripDAO.getTripById(tripId), Trip.class);
    }

    public Trip updateTrip(String tripId, Trip trip) throws DisplayableException {
        if (trip == null) {
            throw new DisplayableException("Trip must be set");
        }

        TripDBObject currentTrip = tripDAO.getTripById(tripId);
        if (currentTrip == null) {
            throw new DisplayableException("Trip with id " + tripId + " could not be found");
        }

        TripDBObject changedTrip = mapper.map(trip, TripDBObject.class);

        //We never update the id, created and updated timestamps here
        changedTrip.setTripId(null);
        changedTrip.setCreated(null);
        changedTrip.setLastUpdated(null);

        try {
            currentTrip.updateFrom(changedTrip);
        } catch (InvocationTargetException | IllegalAccessException e) {
            String message = "Could not update trip!";
            logger.warn(message, e);
            throw new DisplayableException(message, e);
        }

        tripDAO.updateTrip(tripId, currentTrip);

        return mapper.map(tripDAO.getTripById(tripId), Trip.class);
    }

    public boolean deleteTripWithId(String tripId) {
        TripDBObject tripDBObject = tripDAO.getTripById(tripId);

        if (tripDBObject == null) {
            return false;
        }

        WriteResult tripResult = tripDAO.deleteTrip(tripDBObject);
        if (tripResult.getN() == 1) {
            stepController.deleteAllStepsOfTrip(tripId);
        }

        return tripResult.getN() == 1 && tripResult.getError() == null;
    }

    // TODO implement method
    public List<GpsPoint> getAllGpsPointsOfTrip(String tripId) {
        throw new NotImplementedException();
    }

    private void addStepsToTrip(Trip trip) {
        trip.setSteps(stepController.getAllStepsOfTrip(trip.getTripId()));
    }
}
