---
name: github-memory
description: >-
  Recupera, sincroniza y persiste la memoria del proyecto directamente desde y hacia GitHub. Inspecciona commits, ramas, tags remotos, GEMINI.md y .workspace_context.md para no olvidar el contexto entre sesiones.
---

# GitHub Memory & Context Sync Skill

## Propósito
Esta skill permite al agente mantener memoria persistente respaldada directamente en el repositorio de GitHub. Garantiza que el contexto, decisiones técnicas, estado de despliegues y avances nunca se pierdan entre sesiones o cambios de entorno.

## Flujo de Trabajo

### 1. Carga Inicial de Memoria (Al iniciar o cuando se pregunte por el historial)
1. Ejecutar `git log -n 10 --oneline` para inspeccionar los últimos cambios y estado del proyecto.
2. Leer el archivo `GEMINI.md` y `.workspace_context.md` en la raíz del repositorio.
3. Verificar si hay cambios en el remoto de GitHub con `git status` y `git fetch origin`.
4. Resumir mentalmente:
   - ¿Qué servicios están activos (Render, VPS, etc.)?
   - ¿Qué problemas/bugs ya fueron diagnosticados y resueltos (fugas de datos, CI/CD, etc.)?
   - ¿Qué decisiones y preferencias del usuario están vigentes?

### 2. Guardado y Sincronización en GitHub (Al completar un hito)
1. Actualizar `GEMINI.md` y `.workspace_context.md` con los nuevos cambios.
2. Hacer commit estructurado:
   ```bash
   git add GEMINI.md .workspace_context.md
   git commit -m "docs: actualizar memoria del proyecto en GitHub"
   git push origin main
   ```
3. Confirmar que el remoto de GitHub contiene la versión más reciente del contexto.
