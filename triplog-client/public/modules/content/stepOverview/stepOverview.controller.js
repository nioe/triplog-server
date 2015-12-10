'use strict';

// @ngInject
function StepOverviewController($rootScope, $state, trip) {
    var vm = this;
    vm.trip = trip;
    vm.editMode = $state.params.edit && $rootScope.loggedIn;

    $state.current.data.pageTitle = vm.trip.displayName;

    vm.templateToShow = function () {
        return vm.editMode ? 'stepOverview.edit.tpl.html' : 'stepOverview.view.tpl.html';
    };

    vm.deleteTrip = function () {
        console.log('Deleting trip with id', trip.tripId);
    };
}

module.exports = StepOverviewController;