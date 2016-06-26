(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('instructorDashboard', {
            bindings: {
                courseId: '<',
                exerciseId: '<'
            },
            controller: InstructorDashboardController,
            templateUrl: 'app/instructor-dashboard/instructor-dashboard.html'
        });

    InstructorDashboardController.$inject = ['ExerciseResults'];

    function InstructorDashboardController (ExerciseResults) {
        var vm = this;

        vm.showAllResults = false;

        vm.$onInit = init;
        vm.export = exportData;
        vm.refresh = getResults;
        vm.toggleShowAllResults = toggleShowAllResults;

        function init() {
            getResults();
        }

        function exportData() {
            if (vm.results.length > 0) {
                var rows = [];
                vm.results.forEach(function (result, index) {
                    var studentName = result.participation.student.firstName;
                    rows.push(index === 0 ? 'data:text/csv;charset=utf-8,' + studentName : studentName);
                });
                var csvContent = rows.join('\n');
                var encodedUri = encodeURI(csvContent);
                var link = document.createElement('a');
                link.setAttribute('href', encodedUri);
                link.setAttribute('download', 'results.csv');
                document.body.appendChild(link); // Required for FF
                link.click();
            }
        }

        function getResults() {
            vm.results = ExerciseResults.query({
                courseId: vm.courseId,
                exerciseId: vm.exerciseId,
                showAllResults: vm.showAllResults
            });
        }

        function toggleShowAllResults(newValue) {
            vm.showAllResults = newValue;
            console.log(vm.showAllResults);
            getResults();
        }
    }
})();