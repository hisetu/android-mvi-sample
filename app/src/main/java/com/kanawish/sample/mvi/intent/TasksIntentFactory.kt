package com.kanawish.sample.mvi.intent

import com.kanawish.sample.mvi.model.FilterType
import com.kanawish.sample.mvi.model.SyncState
import com.kanawish.sample.mvi.model.SyncState.IDLE
import com.kanawish.sample.mvi.model.SyncState.PROCESS
import com.kanawish.sample.mvi.model.SyncState.PROCESS.Type.REFRESH
import com.kanawish.sample.mvi.model.Task
import com.kanawish.sample.mvi.model.TaskEditorModelStore
import com.kanawish.sample.mvi.model.TasksModelStore
import com.kanawish.sample.mvi.model.TasksState
import com.kanawish.sample.mvi.model.backend.TasksRestApi
import com.kanawish.sample.mvi.view.tasks.TasksViewEvent
import com.kanawish.sample.mvi.view.tasks.TasksViewEvent.ClearCompletedClick
import com.kanawish.sample.mvi.view.tasks.TasksViewEvent.CompleteTaskClick
import com.kanawish.sample.mvi.view.tasks.TasksViewEvent.EditTaskClick
import com.kanawish.sample.mvi.view.tasks.TasksViewEvent.FilterTypeClick
import com.kanawish.sample.mvi.view.tasks.TasksViewEvent.NewTaskClick
import com.kanawish.sample.mvi.view.tasks.TasksViewEvent.RefreshTasksClick
import com.kanawish.sample.mvi.view.tasks.TasksViewEvent.RefreshTasksSwipe
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TasksIntentFactory is responsible for turning TasksViewEvent into
 * Intent<TasksState>, and coordinates with any other dependencies such as
 * ModelStores, Repositories or Services.
 *
 * NOTE: TasksIntentFactory will take the `assert()` validity approach.
 *
 * @see AddEditTaskIntentFactory for state machine safety example.
 */
@Singleton class TasksIntentFactory @Inject constructor(
    private val tasksModelStore: TasksModelStore,
    private val taskEditorModelStore: TaskEditorModelStore,
    private val tasksRestApi: TasksRestApi
) {

    fun process(event: TasksViewEvent) {
        tasksModelStore.process(toIntent(event))
    }

    private fun toIntent(viewEvent:TasksViewEvent): Intent<TasksState> {
        return when (viewEvent) {
            ClearCompletedClick -> buildClearCompletedIntent()
            FilterTypeClick -> buildCycleFilterIntent()
            RefreshTasksSwipe, RefreshTasksClick -> buildReloadTasksIntent()
            is CompleteTaskClick -> buildCompleteTaskClick(viewEvent)
            NewTaskClick -> buildNewTaskIntent()
            is EditTaskClick -> buildEditTaskIntent(viewEvent)
        }
    }

    private fun buildEditTaskIntent(viewEvent: EditTaskClick): Intent<TasksState> {
        // NOTE: We use `sideEffect{}` since we're entirely delegating the work.
        return sideEffect {
            // We can assert things about the TasksStore state.
            assert(tasks.contains(viewEvent.task))

            // Editing a task then only involves opening it.
            val intent = AddEditTaskIntentFactory.buildEditTaskIntent(viewEvent.task)
            taskEditorModelStore.process(intent)
        }
    }

    private fun buildNewTaskIntent(): Intent<TasksState> {
        // NOTE: We use `sideEffect{}` since we're entirely delegating the work.
        return sideEffect {
            // Opening a new task for editing.
            val intent = AddEditTaskIntentFactory.buildAddTaskIntent(Task())
            taskEditorModelStore.process(intent)
        }
    }

    private fun buildCompleteTaskClick(viewEvent: CompleteTaskClick): Intent<TasksState> {
        return intent {
            // We need to operate on the tasks list.
            val mutableList = tasks.toMutableList()
            // Replaces old task in the list with a new updated copy.
            mutableList[tasks.indexOf(viewEvent.task)] = viewEvent.task.copy(completed = viewEvent.checked)
            // Take the modified list, and create a new copy of tasksState with it.
            copy(tasks = mutableList)
        }
    }

    private fun buildReloadTasksIntent(): Intent<TasksState> {
        return object:Intent<TasksState> {
            override fun reducers(): Observable<Reducer<TasksState>> = buildRefreshReducers()
        }
    }

    private fun buildRefreshReducers(): Observable<Reducer<TasksState>> {
        return Observable.create<Reducer<TasksState>> { e ->
            fun retrofitSuccess(loadedTasks: List<Task>) {
                e.onNext { previousState ->
                    assert(previousState.syncState == PROCESS(REFRESH))
                    previousState.copy(tasks = loadedTasks, syncState = IDLE)
                }
            }

            fun retrofitError(throwable: Throwable) {
                e.onNext { previousState ->
                    assert(previousState.syncState == PROCESS(REFRESH))
                    previousState.copy(syncState = SyncState.ERROR(throwable))
                }
            }

            e.onNext { previousState ->
                assert(previousState.syncState == IDLE)

                tasksRestApi.getTasks()
                    .map { it.values.toList() }
                    .subscribeOn(Schedulers.io())
                    .subscribe(::retrofitSuccess, ::retrofitError, e::onComplete)

                previousState.copy(syncState = PROCESS(REFRESH))
            }
        }
    }

    private fun buildCycleFilterIntent(): Intent<TasksState> {
        return intent {
            copy(
                    filter = when (filter) {
                        FilterType.ANY -> FilterType.ACTIVE
                        FilterType.ACTIVE -> FilterType.COMPLETE
                        FilterType.COMPLETE -> FilterType.ANY
                    }
            )
        }
    }

    private fun buildClearCompletedIntent(): Intent<TasksState> {
        return intent {
            copy(tasks = tasks.filter { !it.completed }.toList())
        }
    }

    companion object {
        /** Allows an external Model to save a task. */
        fun buildAddOrUpdateTaskIntent(task: Task): Intent<TasksState> = intent {
            tasks.toMutableList().let { newList ->
                newList.find { task.id == it.id }?.let {
                    newList[newList.indexOf(it)] = task
                } ?: newList.add(task)

                copy(tasks = newList)
            }
        }

        /** Allows an external model to delete a task. */
        fun buildDeleteTaskIntent(taskId: String): Intent<TasksState> = intent {
            copy(tasks = tasks.toMutableList().apply {
                find { it.id == taskId }?.also { remove(it) }
            })
        }
    }

}