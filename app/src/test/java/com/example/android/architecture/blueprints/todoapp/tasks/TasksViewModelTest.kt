/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.architecture.blueprints.todoapp.tasks

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.FlowTurbine
import app.cash.turbine.test
import com.example.android.architecture.blueprints.todoapp.ADD_EDIT_RESULT_OK
import com.example.android.architecture.blueprints.todoapp.DELETE_RESULT_OK
import com.example.android.architecture.blueprints.todoapp.EDIT_RESULT_OK
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the implementation of [TasksViewModel]
 */
@ExperimentalCoroutinesApi
class TasksViewModelTest {

    // Subject under test
    private lateinit var tasksViewModel: TasksViewModel

    // Use a fake repository to be injected into the viewmodel
    private lateinit var tasksRepository: FakeRepository

    // Set the main coroutines dispatcher for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Before
    fun setupViewModel() {
        // We initialise the tasks to 3, with one active and two completed
        tasksRepository = FakeRepository()
        val task1 = Task("Title1", "Description1")
        val task2 = Task("Title2", "Description2", true)
        val task3 = Task("Title3", "Description3", true)
        tasksRepository.addTasks(task1, task2, task3)

        tasksViewModel = TasksViewModel(tasksRepository, SavedStateHandle())
    }

    @Test
    fun loadAllTasksFromRepository_loadingTogglesAndDataLoaded() = runTest {
        // Set Main dispatcher to not run coroutines eagerly, for just this one test
        Dispatchers.setMain(StandardTestDispatcher())

        // Given an initialized TasksViewModel with initialized tasks
        // When loading of Tasks is requested
        tasksViewModel.process(Action.SetFilter(TasksFilterType.ALL_TASKS))

        // Trigger loading of tasks
        tasksViewModel.process(Action.Refresh)

        // Then progress indicator is shown
        assertThat(tasksViewModel.uiState.first().isLoading).isTrue()

        // Execute pending coroutines actions
        advanceUntilIdle()

        // Then progress indicator is hidden
        assertThat(tasksViewModel.uiState.first().isLoading).isFalse()

        // And data correctly loaded
        assertThat(tasksViewModel.uiState.first().items).hasSize(3)
    }

    @Test
    fun loadActiveTasksFromRepositoryAndLoadIntoView() = runTest {
        // Given an initialized TasksViewModel with initialized tasks
        // When loading of Tasks is requested
        tasksViewModel.process(Action.SetFilter(TasksFilterType.ACTIVE_TASKS))

        // Load tasks
        tasksViewModel.process(Action.Refresh)

        // Then progress indicator is hidden
        assertThat(tasksViewModel.uiState.first().isLoading).isFalse()

        // And data correctly loaded
        assertThat(tasksViewModel.uiState.first().items).hasSize(1)
    }

    @Test
    fun loadCompletedTasksFromRepositoryAndLoadIntoView() = runTest {
        // Given an initialized TasksViewModel with initialized tasks
        // When loading of Tasks is requested
        tasksViewModel.process(Action.SetFilter(TasksFilterType.COMPLETED_TASKS))

        // Load tasks
        tasksViewModel.process(Action.Refresh)

        // Then progress indicator is hidden
        assertThat(tasksViewModel.uiState.first().isLoading).isFalse()

        // And data correctly loaded
        assertThat(tasksViewModel.uiState.first().items).hasSize(2)
    }

    @Test
    fun loadTasks_error() = runTest {
        // Make the repository return errors
        tasksRepository.setReturnError(true)

        // Load tasks
        tasksViewModel.process(Action.Refresh)

        // Then progress indicator is hidden
        assertThat(tasksViewModel.uiState.first().isLoading).isFalse()

        // And the list of items is empty
        assertThat(tasksViewModel.uiState.first().items).isEmpty()
        assertThat(tasksViewModel.uiState.first().userMessage).isEqualTo(R.string.loading_tasks_error)
    }

    @Test
    fun clearCompletedTasks_clearsTasks() = runTest {
        tasksViewModel.uiState.test {
            // Await initial state
            awaitItem()

            // When completed tasks are cleared
            tasksViewModel.process(Action.ClearCompletedTasks)

            // Fetch tasks
            tasksViewModel.process(Action.Refresh)
            val uiState = states()
                .filter {
                    it.userMessage == R.string.completed_tasks_cleared
                            && it.items.none(Task::isCompleted)
                }
                .first()

            // Fetch tasks
            val allTasks = uiState.items
            val completedTasks = allTasks?.filter { it.isCompleted }

            // Verify there are no completed tasks left
            assertThat(completedTasks).isEmpty()

            // Verify active task is not cleared
            assertThat(allTasks).hasSize(1)

            // Verify snackbar is updated
            assertThat(uiState.userMessage)
                .isEqualTo(R.string.completed_tasks_cleared)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun showEditResultMessages_editOk_snackbarUpdated() = runTest {
        // When the viewmodel receives a result from another destination
        tasksViewModel.uiState.test {
            assertNull(awaitItem().userMessage)

            tasksViewModel.process(Action.ShowEditResultMessage(EDIT_RESULT_OK))

            // The snackbar is updated
            assertThat(awaitItem().userMessage)
                .isEqualTo(R.string.successfully_saved_task_message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun showEditResultMessages_addOk_snackbarUpdated() = runTest {
        tasksViewModel.uiState.test {
            // Await initial state
            awaitItem()

            // When the viewmodel receives a result from another destination
            tasksViewModel.process(Action.ShowEditResultMessage(ADD_EDIT_RESULT_OK))

            // The snackbar is updated
            assertThat(awaitItem().userMessage)
                .isEqualTo(R.string.successfully_added_task_message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun showEditResultMessages_deleteOk_snackbarUpdated() = runTest {
        tasksViewModel.uiState.test {
            // Await initial state
            awaitItem()

            // When the viewmodel receives a result from another destination
            tasksViewModel.process(Action.ShowEditResultMessage(DELETE_RESULT_OK))

            // The snackbar is updated
            assertThat(awaitItem().userMessage)
                .isEqualTo(R.string.successfully_deleted_task_message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun completeTask_dataAndSnackbarUpdated() = runTest {
        // With a repository that has an active task
        val task = Task("Title", "Description")
        tasksRepository.addTasks(task)

        tasksViewModel.uiState.test {
            // Await initial state
            awaitItem()

            // Complete task
            tasksViewModel.process(Action.SetTaskCompletion(task = task, completed = true))

            // Verify the task is completed
            assertThat(tasksRepository.savedTasks.value[task.id]?.isCompleted).isTrue()

            // The snackbar is updated
            assertThat(awaitItem().userMessage)
                .isEqualTo(R.string.task_marked_complete)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun activateTask_dataAndSnackbarUpdated() = runTest {
        // With a repository that has a completed task
        val task = Task("Title", "Description", true)
        tasksRepository.addTasks(task)

        tasksViewModel.uiState.test {
            // Await initial state
            awaitItem()

            // Activate task
            tasksViewModel.process(Action.SetTaskCompletion(task = task, completed = false))

            // Verify the task is active
            assertThat(tasksRepository.savedTasks.value[task.id]?.isActive).isTrue()

            // The snackbar is updated
            assertThat(awaitItem().userMessage)
                .isEqualTo(R.string.task_marked_active)

            cancelAndIgnoreRemainingEvents()
        }
    }
}

private fun FlowTurbine<TasksUiState>.states() =
    flow { while (true) emit(awaitItem()) }
