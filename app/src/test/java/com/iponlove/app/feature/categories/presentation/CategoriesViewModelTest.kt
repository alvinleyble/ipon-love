package com.iponlove.app.feature.categories.presentation

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ArchiveCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.CheckCategoryCapUseCase
import com.iponlove.app.feature.categories.domain.usecase.DeleteCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.categories.domain.usecase.ReorderCategoriesUseCase
import com.iponlove.app.feature.categories.domain.usecase.ShareCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.UnshareCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.UpsertCategoryUseCase
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.transactions.domain.usecase.CountTransactionsForCategoryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Covers only the v1.7.0 Item 8 create-counterpart branch of [CategoriesViewModel.save] — the
 * rest of the ViewModel is pre-existing and unchanged by this slice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { observeCategories(includeArchived = true) } returns flowOf(emptyList())
        every { observeCoupleMembers() } returns flowOf(null)
        coEvery { checkCategoryCap(shared = false) } returns CapCheck.Allowed
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val observeCategories: ObserveCategoriesUseCase = mockk()
    private val observeCoupleMembers: ObserveCoupleMembersUseCase = mockk()
    private val upsertCategory: UpsertCategoryUseCase = mockk(relaxed = true)
    private val archiveCategory: ArchiveCategoryUseCase = mockk(relaxed = true)
    private val deleteCategory: DeleteCategoryUseCase = mockk(relaxed = true)
    private val shareCategory: ShareCategoryUseCase = mockk(relaxed = true)
    private val unshareCategory: UnshareCategoryUseCase = mockk(relaxed = true)
    private val reorderCategories: ReorderCategoriesUseCase = mockk(relaxed = true)
    private val checkCategoryCap: CheckCategoryCapUseCase = mockk()
    private val countTransactionsForCategory: CountTransactionsForCategoryUseCase = mockk(relaxed = true)
    private val analytics: Analytics = mockk(relaxed = true)

    private fun viewModel() = CategoriesViewModel(
        observeCategories = observeCategories,
        observeCoupleMembers = observeCoupleMembers,
        upsertCategory = upsertCategory,
        archiveCategory = archiveCategory,
        deleteCategory = deleteCategory,
        shareCategory = shareCategory,
        unshareCategory = unshareCategory,
        reorderCategories = reorderCategories,
        checkCategoryCap = checkCategoryCap,
        countTransactionsForCategory = countTransactionsForCategory,
        analytics = analytics,
    )

    @Test
    fun save_newExpenseCategoryWithExcludeFlag_opensIncomeCounterpartPrompt() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.startCreate()
        vm.onNameChange("Client lunch")
        vm.onExcludeFromAnalysisChange(true)

        vm.save()

        val pending = vm.uiState.value.pendingCounterpart
        assertThat(pending).isNotNull()
        assertThat(pending!!.stage).isEqualTo(CounterpartStage.ASK)
        assertThat(pending.counterpartType).isEqualTo(CategoryType.INCOME)
        coVerify(exactly = 1) { upsertCategory(match { it.name == "Client lunch" && it.excludeFromAnalysis }) }
    }

    @Test
    fun save_newIncomeCategoryWithExcludeFlag_opensExpenseCounterpartPrompt() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.startCreate()
        vm.onTypeChange(CategoryType.INCOME)
        vm.onNameChange("Phone allowance")
        vm.onExcludeFromAnalysisChange(true)

        vm.save()

        assertThat(vm.uiState.value.pendingCounterpart?.counterpartType).isEqualTo(CategoryType.EXPENSE)
    }

    @Test
    fun save_newCategoryWithoutExcludeFlag_doesNotOpenCounterpartPrompt() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.startCreate()
        vm.onNameChange("Groceries")

        vm.save()

        assertThat(vm.uiState.value.pendingCounterpart).isNull()
    }

    @Test
    fun save_editOfExistingFlaggedCategory_doesNotOpenCounterpartPrompt() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        val existing = Category(
            id = "cat-1",
            name = "Reimbursable",
            type = CategoryType.EXPENSE,
            excludeFromAnalysis = true,
        )
        vm.startEdit(existing)

        vm.save()

        assertThat(vm.uiState.value.pendingCounterpart).isNull()
    }

    @Test
    fun confirmCounterpartName_blank_setsErrorWithoutCreating() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.startCreate()
        vm.onNameChange("Client lunch")
        vm.onExcludeFromAnalysisChange(true)
        vm.save()
        vm.counterpartPromptYes()

        vm.confirmCounterpartName()

        val pending = vm.uiState.value.pendingCounterpart
        assertThat(pending).isNotNull()
        assertThat(pending!!.stage).isEqualTo(CounterpartStage.NAME_INPUT)
        assertThat(pending.nameError).isTrue()
        coVerify(exactly = 1) { upsertCategory(any()) } // only the original save, no counterpart yet
    }

    @Test
    fun counterpartFlow_yesThenName_createsCounterpartWithInheritedIconAndColor() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.startCreate()
        vm.onNameChange("Client lunch")
        vm.onIconChange("restaurant")
        vm.onColorChange("#FF00FF")
        vm.onExcludeFromAnalysisChange(true)
        vm.save()

        vm.counterpartPromptYes()
        vm.onCounterpartNameChange("Reimbursement")
        vm.confirmCounterpartName()

        assertThat(vm.uiState.value.pendingCounterpart).isNull()
        coVerify(exactly = 1) {
            upsertCategory(
                match {
                    it.name == "Reimbursement" &&
                        it.type == CategoryType.INCOME &&
                        it.icon == "restaurant" &&
                        it.color == "#FF00FF" &&
                        it.excludeFromAnalysis
                },
            )
        }
    }

    @Test
    fun dismissCounterpartPrompt_clearsPromptWithoutCreatingCounterpart() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.startCreate()
        vm.onNameChange("Client lunch")
        vm.onExcludeFromAnalysisChange(true)
        vm.save()

        vm.dismissCounterpartPrompt()

        assertThat(vm.uiState.value.pendingCounterpart).isNull()
        coVerify(exactly = 1) { upsertCategory(any()) } // only the original — no counterpart created
    }
}
