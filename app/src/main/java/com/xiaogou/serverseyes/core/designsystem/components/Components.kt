package com.xiaogou.serverseyes.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    borderColor: Color = AppTheme.colors.border,
    backgroundColor: Color = AppTheme.colors.surface,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.space3),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(AppRadius.md)
    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            border = border
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            border = border
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AppTheme.typography.titleSmall,
            color = AppTheme.colors.textPrimary
        )
        trailing?.invoke()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val colors = AppTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(AppRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.actionPrimary,
            contentColor = colors.onActionPrimary,
            disabledContainerColor = colors.surfaceSubtle,
            disabledContentColor = colors.textDisabled
        ),
        contentPadding = PaddingValues(horizontal = AppSpacing.space4, vertical = AppSpacing.space3)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.onActionPrimary
            )
            Spacer(Modifier.width(AppSpacing.space2))
        } else if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.space2))
        }
        Text(text = text, style = AppTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val colors = AppTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(AppRadius.md),
        border = BorderStroke(1.dp, if (enabled) colors.borderStrong else colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textDisabled
        ),
        contentPadding = PaddingValues(horizontal = AppSpacing.space4, vertical = AppSpacing.space3)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.actionPrimary
            )
            Spacer(Modifier.width(AppSpacing.space2))
        } else if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.space2))
        }
        Text(text = text, style = AppTheme.typography.labelLarge)
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = AppTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(AppRadius.md),
        border = BorderStroke(1.dp, colors.error),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.errorSoft,
            contentColor = colors.error
        ),
        contentPadding = PaddingValues(horizontal = AppSpacing.space3, vertical = AppSpacing.space2)
    ) {
        Text(text = text, style = AppTheme.typography.labelLarge)
    }
}

@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    errorText: String? = null,
    isSecret: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    keyboardOptions: KeyboardOptions = if (isSecret) {
        KeyboardOptions(keyboardType = KeyboardType.Password)
    } else {
        KeyboardOptions.Default
    }
) {
    val colors = AppTheme.colors
    var secretVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.space1)) {
        Text(
            text = label,
            style = AppTheme.typography.labelLarge,
            color = colors.textPrimary
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder, style = AppTheme.typography.bodyMedium, color = colors.textDisabled) }
            } else null,
            isError = !errorText.isNullOrBlank(),
            singleLine = singleLine,
            maxLines = maxLines,
            visualTransformation = if (isSecret && !secretVisible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isSecret) {
                {
                    IconButton(onClick = { secretVisible = !secretVisible }) {
                        Icon(
                            imageVector = if (secretVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (secretVisible) "隐藏密码" else "显示密码",
                            tint = colors.textSecondary
                        )
                    }
                }
            } else null,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(AppRadius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.actionPrimary,
                unfocusedBorderColor = colors.border,
                errorBorderColor = colors.error,
                focusedContainerColor = colors.surfaceSubtle,
                unfocusedContainerColor = colors.surfaceSubtle,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            textStyle = AppTheme.typography.bodyLarge
        )
        if (!errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                style = AppTheme.typography.bodySmall,
                color = colors.error
            )
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDangerous: Boolean = false
) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = if (isDangerous) colors.error else colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text(text = title, style = AppTheme.typography.titleMedium) },
        text = { Text(text = message, style = AppTheme.typography.bodyMedium) },
        confirmButton = {
            if (isDangerous) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.error,
                        contentColor = Color.White
                    )
                ) {
                    Text(confirmText)
                }
            } else {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.actionPrimary,
                        contentColor = colors.onActionPrimary
                    )
                ) {
                    Text(confirmText)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = colors.textSecondary)
            }
        }
    )
}
