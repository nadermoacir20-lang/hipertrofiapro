# Hipertrofia Pro — formato de patch AI

A aplicação aceita um único objecto JSON com:

```json
{
  "type": "hipertrofia-pro-patch",
  "patchVersion": 1,
  "label": "Descrição curta",
  "changes": []
}
```

## Operações

### update
Actualiza apenas os campos indicados de um exercício existente.

```json
{"op":"update","day":"Segunda","id":"mon-smith","set":{"targetWeight":"17.5","sets":3,"targetReps":"8–12","rest":150,"execution":"...","progression":"...","notes":"..."}}
```

Campos aceites: `name`, `sets`, `targetWeight`, `targetReps`, `loadMode`, `rest`, `warmup`, `execution`, `progression`, `notes`, `active`.

### pause / resume

```json
{"op":"pause","day":"Segunda","id":"mon-adductor"}
{"op":"resume","day":"Segunda","id":"mon-adductor"}
```

### add

```json
{
  "op":"add",
  "day":"Quarta",
  "position":3,
  "exercise":{
    "id":"wed-new-exercise",
    "name":"Novo exercício",
    "active":true,
    "sets":3,
    "targetWeight":"20",
    "loadMode":"total",
    "targetReps":"8–12",
    "rest":90,
    "warmup":{"sets":1,"weight":"10","reps":"12"},
    "execution":"...",
    "progression":"...",
    "notes":"..."
  }
}
```

### remove

```json
{"op":"remove","day":"Quarta","id":"wed-new-exercise"}
```

### reorder

IDs omitidos permanecem no fim pela ordem anterior.

```json
{"op":"reorder","day":"Segunda","ids":["mon-smith","mon-legpress","mon-legcurl-seated"]}
```

### day

```json
{"op":"day","day":"Sexta","set":{"title":"UPPER + FUTSAL","focus":"Volume controlado"}}
```

## Regra fundamental

Não mudar IDs de exercícios existentes. O ID liga o plano ao histórico; mudar o ID faria o exercício parecer novo.
