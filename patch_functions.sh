#!/bin/bash
awk '
/testTag\("functions_game_backup_row"\)/ { found = 1 }
found && /HorizontalDivider\(color = dividerColor, thickness = 0.8.dp\)/ {
    print
    if (!inserted) {
        inserted = 1
        print "        // Function 4: Solución (Entrar a la interfaz, deshabilitado si no hay copia)"
        print "        val context = LocalContext.current"
        print "        val hasGameBackup = com.example.data.GameBackupManager.getInstance(context).getHasCompletedBackup()"
        print "        Row("
        print "          modifier = Modifier"
        print "            .fillMaxWidth()"
        print "            .clickable("
        print "              interactionSource = remember { MutableInteractionSource() },"
        print "              indication = null,"
        print "              enabled = hasGameBackup,"
        print "              onClick = onOpenSolution"
        print "            )"
        print "            .padding(vertical = 12.dp)"
        print "            .alpha(if (hasGameBackup) 1f else 0.4f)"
        print "            .testTag(\"functions_solution_row\"),"
        print "          verticalAlignment = Alignment.CenterVertically"
        print "        ) {"
        print "          Column(modifier = Modifier.weight(1f)) {"
        print "            Text("
        print "              text = stringResource(R.string.functions_solution_title),"
        print "              fontSize = 15.sp,"
        print "              fontWeight = FontWeight.Medium,"
        print "              color = onSurfaceColor"
        print "            )"
        print "            Text("
        print "              text = stringResource(R.string.functions_solution_subtitle),"
        print "              fontSize = 12.sp,"
        print "              color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)"
        print "            )"
        print "          }"
        print "          Spacer(modifier = Modifier.width(12.dp))"
        print "          Icon("
        print "            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,"
        print "            contentDescription = null,"
        print "            tint = if (isDarkMode) GoldButtonColor else Color(0xFF9CA3AF),"
        print "            modifier = Modifier.size(14.dp)"
        print "          )"
        print "        }"
        print "        HorizontalDivider(color = dividerColor, thickness = 0.8.dp)"
    }
    found = 0
    next
}
{ print }
' app/src/main/java/com/example/ui/screens/FunctionsScreen.kt > temp.kt && mv temp.kt app/src/main/java/com/example/ui/screens/FunctionsScreen.kt
