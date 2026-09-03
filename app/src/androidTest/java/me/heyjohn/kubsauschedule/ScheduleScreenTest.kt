package me.heyjohn.kubsauschedule

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Живой сценарий: ввод группы с клавиатуры, поиск, появление расписания.
 * Требует доступа к s.kubsau.ru с устройства.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun searchByGroupShowsSchedule() {
        rule.onNodeWithTag(TestTags.GROUP_FIELD).performTextReplacement("БИ2601")
        rule.onNodeWithTag(TestTags.GROUP_FIELD).performImeAction()

        rule.waitUntil(timeoutMillis = 60_000) {
            rule.onAllNodesWithText("Понедельник").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun typingShowsSuggestions() {
        rule.onNodeWithTag(TestTags.GROUP_FIELD).performTextReplacement("")
        rule.onNodeWithTag(TestTags.GROUP_FIELD).performTextInput("БИ26")

        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithText("БИ2601").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
