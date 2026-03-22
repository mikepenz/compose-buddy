package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.inspector.model.ComparisonReport

@Composable
fun ComparisonReportView(
    report: ComparisonReport,
    onMismatchSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Comparison Report", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Match: ${report.summary.matchCount}/${report.summary.totalTokens} (${"%.1f".format(report.summary.matchPercentage)}%)",
            fontSize = 12.sp,
            color = if (report.summary.matchPercentage > 90) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (report.mismatched.isNotEmpty()) {
            Text(
                "Mismatched (${report.mismatched.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
            )
            for (mismatch in report.mismatched) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMismatchSelected(mismatch.tokenName) }
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        .padding(4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mismatch.tokenName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Figma: ${mismatch.figmaValue}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        Text("Compose: ${mismatch.composeValue}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        mismatch.deltaE?.let {
                            Text("Delta E: ${"%.2f".format(it)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (report.matched.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "Matched (${report.matched.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
            for (match in report.matched) {
                Text("${match.tokenName}: ${match.value}", fontSize = 10.sp)
            }
        }

        if (report.missingInCompose.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "Missing in Compose (${report.missingInCompose.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.tertiary,
            )
            for (name in report.missingInCompose) {
                Text(name, fontSize = 10.sp)
            }
        }

        if (report.missingInFigma.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "Missing in Figma (${report.missingInFigma.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
            )
            for (name in report.missingInFigma) {
                Text(name, fontSize = 10.sp)
            }
        }
    }
}
