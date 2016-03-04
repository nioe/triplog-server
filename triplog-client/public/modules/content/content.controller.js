'use strict';

// @ngInject
function ContentController($rootScope, $state, $window, ENV, EVENT_NAMES, loadTripsFromLocalStorage, TripsService, LoginService, AlertService, showModal) {

    var vm = this;
    vm.environment = ENV;
    vm.trips = loadTripsFromLocalStorage();

    vm.navigationIsShown = false;
    vm.isIosFullscreen = $window.navigator.standalone ? true : false;

    vm.openPicture = function (imageName) {
        $rootScope.$emit('triplogOpenPicture', imageName);
    };

    createNavigation();

    // React on state changes
    $rootScope.$on('$stateChangeStart', vm.closeNavigation);
    $rootScope.$on('$stateChangeSuccess', createNavigation);
    $rootScope.$on(EVENT_NAMES.loginStateChanged, createNavigation);

    // Reload trips into memory if local storage changed
    $rootScope.$on(EVENT_NAMES.localStorageUpdated, loadTripsAndCreateNavigaton);

    vm.toggleNavigation = function () {
        if (vm.navigationIsShown) {
            vm.closeNavigation();
        } else {
            vm.openNavigation();
        }
    };

    vm.closeNavigation = function () {
        if (isDeviceWithSideNavigation() && vm.navigationIsShown) {
            vm.navigationIsShown = false;
        }
    };

    vm.openNavigation = function () {
        if (isDeviceWithSideNavigation() && !vm.navigationIsShown) {
            vm.navigationIsShown = true;
        }
    };

    vm.logout = function () {
        vm.closeNavigation();
        LoginService.logout().then(function () {
            $state.go('content.allTrips', {}, {reload: true});
            AlertService.success('You have been successfully logged out.');
        }, function () {
            AlertService.error('There was an error during the logout process... :( Please try again.');
        });
    };

    /************************************** Private Functions **************************************/
    function loadTripsAndCreateNavigaton() {
        vm.trips = loadTripsFromLocalStorage();
        createNavigation();
    }

    function createNavigation() {
        vm.navBarEntries = [];
        createTripOverviewNavBarEntry();
        createStepOverviewNavBarEntry();
    }

    function createTripOverviewNavBarEntry() {
        var entries = [{
            id: 'overview',
            name: 'Overview',
            icon: 'trip-overview',
            action: function () {
                $state.go('content.allTrips');
            },
            divider: true,
            active: function () {
                return $state.current.name === 'content.allTrips';
            }
        }];

        vm.trips.forEach(function (trip) {
            entries.push({
                id: trip.tripId,
                name: trip.displayName,
                icon: 'trip',
                action: function () {
                    $state.go('content.stepOverview', {tripId: trip.tripId});
                },
                active: function () {
                    return ['content.stepOverview', 'content.stepOfTrip'].indexOf($state.current.name) !== -1 &&
                        $state.params.tripId === trip.tripId;
                }
            });
        });

        if ($rootScope.loggedIn) {
            entries[entries.length - 1].divider = true;
            entries.push({
                id: 'addTrip',
                name: 'Add trip',
                icon: 'add',
                action: function () {
                    console.log('Not yet implemented... :(');
                },
                active: function () {
                    return false; //TODO Implement function
                }
            });
        }

        vm.navBarEntries.push({
            id: 'trips',
            name: 'Trips',
            icon: 'trip-overview',
            entries: entries
        });
    }


    function createStepOverviewNavBarEntry() {
        if (['content.stepOverview', 'content.stepOfTrip'].indexOf($state.current.name) !== -1) {
            var tripId = $state.params.tripId,
                entries = [
                    {
                        id: 'overview',
                        name: 'Overview',
                        icon: 'step-overview',
                        action: function () {
                            $state.go('content.stepOverview', {tripId: tripId, edit: undefined});
                        },
                        divider: true,
                        active: function () {
                            return $state.current.name === 'content.stepOverview' && !$state.params.edit;
                        }
                    }
                ],
                tripIndex = indexOfTripWithId(tripId);

            if (tripIndex >= 0) {
                var createStepEntry = function(step) {
                        entries.push({
                            id: step.stepId,
                            name: step.stepName,
                            icon: 'step',
                            action: function () {
                                $state.go('content.stepOfTrip', {tripId: tripId, stepId: step.stepId});
                            },
                            active: function () {
                                return $state.params.tripId === tripId && $state.params.stepId === step.stepId;
                            }
                        });
                    },
                    steps = vm.trips[tripIndex].steps;

                for (var i = 0; i < Math.min(steps.length, 10); i++) {
                    createStepEntry(steps[i]);
                }

                if ($rootScope.loggedIn) {
                    entries[entries.length - 1].divider = true;
                    entries = entries.concat(tripControls(tripId));
                }

                vm.navBarEntries.push({
                    id: tripId,
                    name: vm.trips[tripIndex].displayName,
                    icon: 'trip',
                    entries: entries
                });
            }
        }
    }

    function indexOfTripWithId(tripId) {
        for (var i = 0; i < vm.trips.length; i++) {
            if (vm.trips[i].tripId === tripId) {
                return i;
            }
        }

        return -1;
    }

    function isDeviceWithSideNavigation() {
        return $window.innerWidth < 768;
    }

    function tripControls(tripId) {
        var controls = [];

        controls.push({
            id: 'editTrip',
            name: 'Edit Trip',
            icon: 'edit',
            action: function () {
                $state.go('content.stepOverview', {tripId: tripId, edit: true});
            },
            active: function () {
                return $state.current.name === 'content.stepOverview' && $state.params.edit;
            }
        });

        controls.push({
            id: 'deleteTrip',
            name: 'Delete Trip',
            icon: 'delete',
            action: function () {
                var trip = vm.trips[indexOfTripWithId(tripId)],
                    deleteTripModalData = {
                        title: 'Delete ' + trip.tripName,
                        message: 'Caution: This cannot be undone. All trip data including all stpes and pictures will be deleted!',
                        okText: 'Delete',
                        okClass: 'btn-danger',
                        cancelText: 'Cancel',
                        cancelClass: 'btn-primary'
                    };

                showModal(deleteTripModalData).then(function () {
                    TripsService.deleteTrip(tripId);
                    $state.go('content.allTrips', {}, {reload: true});
                });
            },
            active: function () {
                return false;
            }
        });

        controls.push({
            id: 'addStep',
            name: 'Add step',
            icon: 'add',
            action: function () {
                console.log('Not yet implemented... :(');
            },
            active: function () {
                return false; //TODO Implement function
            }
        });

        return controls;
    }
}

module.exports = ContentController;