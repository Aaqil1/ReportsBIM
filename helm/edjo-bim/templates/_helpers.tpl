{{/*
Common labels
*/}}
{{- define "edjo-bim.labels" -}}
app.kubernetes.io/name: {{ include "edjo-bim.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "edjo-bim.selectorLabels" -}}
app: {{ include "edjo-bim.name" . }}
{{- end }}

{{/*
Chart name
*/}}
{{- define "edjo-bim.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}
