const fs = require('fs');
const path = require('path');
const filePath = path.join('C:', 'Users', 'phil', 'weather-none', 'app', 'src', 'main', 'java', 'com', 'skypulse', 'weather', 'ui', 'components', 'CityListRow.kt');
let content = fs.readFileSync(filePath, 'utf-8').replace(/\r\n/g, '\n');

// Fix 1: Temperature - always render the Box, show "--" when no data
content = content.replace(
    `            if (weather != null) {
                Box(
                    contentAlignment = Alignment.TopEnd,
                    modifier = Modifier.alignByBaseline()
                ) {
                    Text(
                        text = temperature,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Thin,
                            fontFeatureSettings = "tnum"
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "°",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Thin
                        ),
                        color = TextPrimary,
                        modifier = Modifier.offset(x = 10.dp, y = 2.dp)
                    )
                }
            }`,
    `            Box(
                contentAlignment = Alignment.TopEnd,
                modifier = Modifier.alignByBaseline()
            ) {
                Text(
                    text = if (weather != null) temperature else "--",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Thin,
                        fontFeatureSettings = "tnum"
                    ),
                    color = TextPrimary.copy(alpha = if (weather != null) 1f else 0.4f)
                )
                if (weather != null) {
                    Text(
                        text = "°",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Thin
                        ),
                        color = TextPrimary,
                        modifier = Modifier.offset(x = 10.dp, y = 2.dp)
                    )
                }
            }`
);

// Fix 2: AQI text - always render, show "--" when no data
content = content.replace(
    `        Row(modifier = Modifier.fillMaxWidth()) {
            if (aqiText != null) {
                Text(
                    text = aqiText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (weather != null) {
                Text(
                    text = weatherInfo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }`,
    `        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = aqiText ?: "--",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = if (aqiText != null) 1f else 0.4f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (weather != null) weatherInfo.description else "--",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = if (weather != null) 1f else 0.4f),
                modifier = Modifier.padding(end = 8.dp)
            )
        }`
);

fs.writeFileSync(filePath, content.replace(/\n/g, '\r\n'), 'utf-8');
console.log('CityListRow fixed: layout always stable');
