'use strict';

describe('Process Queue', function () {
    var $rootScope,
        queue,
        localStorageService,
        localStorage,
        PROCESS_QUEUE_STORAGE_KEYS,
        PROCESS_QUEUE_PUSH_EVENT;

    beforeEach(module('processQueue', function ($provide) {
        localStorageService = {
            get: function (key) {
                return localStorage[key];
            },

            set: function (key, value) {
                localStorage[key] = value;
            }
        };

        $provide.value('localStorageService', localStorageService);
    }));

    beforeEach(inject(function (_$rootScope_, _ProcessQueue_, _PROCESS_QUEUE_STORAGE_KEYS_, _PROCESS_QUEUE_PUSH_EVENT_) {
        $rootScope = _$rootScope_;
        queue = _ProcessQueue_;
        PROCESS_QUEUE_STORAGE_KEYS = _PROCESS_QUEUE_STORAGE_KEYS_;
        PROCESS_QUEUE_PUSH_EVENT = _PROCESS_QUEUE_PUSH_EVENT_;
    }));

    it('should enqueue new action', function () {
        // given
        var resourceName = 'TestResource',
            method = 'delete',
            config = {id: 'testId'};

        localStorage = {};

        spyOn($rootScope, '$broadcast');

        // when
        queue.enqueue(resourceName, method, config);

        // then
        var actual = localStorage[PROCESS_QUEUE_STORAGE_KEYS.PROCESS_QUEUE];
        expect(actual.length).toBe(1);
        expect(actual[0]).toEqual({resourceName: resourceName, method: method, config: config, payload: undefined});
        expect($rootScope.$broadcast).toHaveBeenCalledWith(PROCESS_QUEUE_PUSH_EVENT);
    });

    it('should dequeue first item', function () {
        // given
        var dummyQueue = [1, 2, 3, 4, 5];

        localStorage = {};
        localStorage[PROCESS_QUEUE_STORAGE_KEYS.PROCESS_QUEUE] = dummyQueue;

        // when
        var actual = queue.dequeue();

        // then
        expect(actual).toBe(1);
        expect(dummyQueue).toEqual([2, 3, 4, 5]);
    });

    it('should requeue item at the beginning of queue', function () {
        // given
        var dummyQueue = [1, 2, 3, 4, 5];

        localStorage = {};
        localStorage[PROCESS_QUEUE_STORAGE_KEYS.PROCESS_QUEUE] = dummyQueue;

        // when
        queue.requeue(0);

        // then
        expect(dummyQueue).toEqual([0, 1, 2, 3, 4, 5]);
    });

    it('should return correct queue size', function () {
        // given
        var dummyQueue = [1, 2, 3, 4, 5];

        localStorage = {};
        localStorage[PROCESS_QUEUE_STORAGE_KEYS.PROCESS_QUEUE] = dummyQueue;

        // when
        var actual = queue.size();

        // then
        expect(actual).toEqual(dummyQueue.length);
    });

    it('should return indicate if there are items in the queue', function () {
        // given
        var dummyQueue = [1, 2, 3, 4, 5];

        localStorage = {};
        localStorage[PROCESS_QUEUE_STORAGE_KEYS.PROCESS_QUEUE] = dummyQueue;

        // when
        var actual = queue.hasItems();

        // then
        expect(actual).toBeTruthy();
    });

    it('should return indicate if there are no items in the queue', function () {
        // given
        localStorage = {};

        // when
        var actual = queue.hasItems();

        // then
        expect(actual).toBeFalsy();
    });
});