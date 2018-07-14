package com.kanawish.sample.mvi.view.tasks

import android.content.Intent
import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.jakewharton.rxbinding2.support.design.widget.itemSelections
import com.jakewharton.rxbinding2.view.clicks
import com.kanawish.sample.mvi.R
import com.kanawish.sample.mvi.intent.TasksIntentFactory
import com.kanawish.sample.mvi.model.TaskEditorState
import com.kanawish.sample.mvi.model.TaskEditorModelStore
import com.kanawish.sample.mvi.util.replaceFragmentInActivity
import com.kanawish.sample.mvi.util.setupActionBar
import com.kanawish.sample.mvi.view.EventObservable
import com.kanawish.sample.mvi.view.StateSubscriber
import com.kanawish.sample.mvi.view.addedittask.AddEditTaskActivity
import com.kanawish.sample.mvi.view.statistics.StatisticsActivity
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.tasks_act.drawerLayout
import kotlinx.android.synthetic.main.tasks_act.navView
import kotlinx.android.synthetic.main.tasks_act.newTaskFloatingActionButton
import javax.inject.Inject

/**
 * Tasks Activity houses the Toolbar, the nav UI, the FAB and the fragment holding the tasks list.
 */
class TasksActivity : AppCompatActivity(),
    StateSubscriber<TaskEditorState>,
    EventObservable<TasksViewEvent> {

    // NOTE: We connect to _editor_ model here.
    @Inject lateinit var editorModelStore: TaskEditorModelStore

    // NOTE: We still only generate "Tasks" ViewEvents.
    @Inject lateinit var tasksIntentFactory: TasksIntentFactory

    private val disposables = CompositeDisposable()

    /**
     * TasksActivity starts the AddEditTaskActivity when it detects the
     * `TaskEditorStore` has transitioned to a `TaskEditorState.Editing` state.
     */
    override fun Observable<TaskEditorState>.subscribeToState(): Disposable {
        return ofType<TaskEditorState.Editing>().subscribe {
            val intent = Intent(this@TasksActivity, AddEditTaskActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * TasksActivity owns the Floating Action Button, and is the source
     * for `TasksViewEvent.NewTaskClick` events.
     */
    override fun events(): Observable<TasksViewEvent> {
        return newTaskFloatingActionButton.clicks().map { TasksViewEvent.NewTaskClick }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tasks_act)

        // Set up the toolbar.
        setupActionBar(R.id.toolbar) {
            setHomeAsUpIndicator(R.drawable.ic_menu)
            setDisplayHomeAsUpEnabled(true)
        }

        // Set up the navigation drawer.
        drawerLayout.apply {
            setStatusBarBackground(R.color.colorPrimaryDark)
        }

        // Use existing content fragment, or create one from scratch.
        supportFragmentManager.findFragmentById(R.id.contentFrame) as TasksFragment?
            ?: TasksFragment().also {
                replaceFragmentInActivity(it, R.id.contentFrame)
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()

        disposables += subscribeNavHandling()

        disposables += events().subscribe(tasksIntentFactory::process)
        disposables += editorModelStore.modelState().subscribeToState()
    }

    // NOTE: If something doesn't impact your Model/Domain, it's okay to call it "View-logic", and handle internally.
    private fun subscribeNavHandling(): Disposable {
        return navView.itemSelections()
            .subscribe {
                when (it.itemId) {
                    R.id.statistics_navigation_menu_item -> {
                        Intent(this@TasksActivity, StatisticsActivity::class.java)
                            // .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                            .also { startActivity(it) }
                    }
                }
                it.isChecked = true
                drawerLayout.closeDrawers()
            }
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

}