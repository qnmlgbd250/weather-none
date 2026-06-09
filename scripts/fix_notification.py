with open('app/src/main/java/com/skypulse/weather/notification/WeatherNotificationWorker.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
warning_block_count = 0
in_companion = False
companion_brace_count = 0
skip_until = -1

for i, line in enumerate(lines):
    if i < skip_until:
        continue
    
    stripped = line.rstrip('\n')
    
    # Track companion object
    if 'companion object {' in stripped:
        in_companion = True
        companion_brace_count = 1
        new_lines.append(line)
        continue
    
    if in_companion:
        companion_brace_count += stripped.count('{') - stripped.count('}')
        if companion_brace_count == 0:
            in_companion = False
            new_lines.append('        private const val NOTIFICATION_PREFS = "notification_log"\n')
            new_lines.append('        private const val ALERT_EXPIRE_MS = 6 * 60 * 60 * 1000L // 6 hours\n')
            new_lines.append(line)
            new_lines.append('\n')
            new_lines.append('    private fun isDuplicateAlert(context: Context, alertKey: String): Boolean {\n')
            new_lines.append('        val logPrefs = context.getSharedPreferences(NOTIFICATION_PREFS, Context.MODE_PRIVATE)\n')
            new_lines.append('        val lastTime = logPrefs.getLong(alertKey, 0)\n')
            new_lines.append('        val now = System.currentTimeMillis()\n')
            new_lines.append('        return (now - lastTime) < ALERT_EXPIRE_MS\n')
            new_lines.append('    }\n')
            new_lines.append('\n')
            new_lines.append('    private fun markAlertSent(context: Context, alertKey: String) {\n')
            new_lines.append('        val logPrefs = context.getSharedPreferences(NOTIFICATION_PREFS, Context.MODE_PRIVATE)\n')
            new_lines.append('        logPrefs.edit().putLong(alertKey, System.currentTimeMillis()).apply()\n')
            new_lines.append('    }\n')
            continue
        new_lines.append(line)
        continue
    
    # Add alertIndex variable in warning block
    if 'alerts?.forEach { alert ->' in stripped and warning_block_count == 0:
        new_lines.append('                var alertIndex = 0\n')
        new_lines.append(line)
        warning_block_count += 1
        continue
    
    # Replace the warning notification block
    if 'if (!cleanTitle.isNullOrBlank())' in stripped and warning_block_count == 1:
        indent = '                        '
        new_lines.append(indent + 'if (!cleanTitle.isNullOrBlank()) {\n')
        new_lines.append(indent + '    val alertKey = "alert_${cleanTitle.hashCode()}"\n')
        new_lines.append(indent + '    if (!isDuplicateAlert(context, alertKey)) {\n')
        new_lines.append(indent + '        val body = "${city.name} $weatherDesc ${temp}\u00b0C | ${minTemp}\u00b0/${maxTemp}\u00b0"\n')
        new_lines.append(indent + '        sendNotification(nm, 100 + alertIndex, cleanTitle, body)\n')
        new_lines.append(indent + '        markAlertSent(context, alertKey)\n')
        new_lines.append(indent + '    }\n')
        new_lines.append(indent + '    alertIndex++\n')
        new_lines.append(indent + '}\n')
        # Skip old lines: val body, sendNotification, closing brace
        skip_until = i + 4  # skip 3 lines after current
        continue
    
    new_lines.append(line)

with open('app/src/main/java/com/skypulse/weather/notification/WeatherNotificationWorker.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

print('Done')