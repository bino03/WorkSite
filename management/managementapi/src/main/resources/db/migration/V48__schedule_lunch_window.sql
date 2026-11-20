-- O almoço passa a picar-se (decisão de 2026-11-20): o funcionário marca entrada e
-- saída de manhã e de tarde, e pode mudar de obra entre as duas. O horário deixa de
-- dizer só "N minutos de pausa" e passa a dizer **quando** é o almoço — é o que
-- permite medir o atraso no regresso e reconhecer um almoço que não foi picado.
--
-- Aditiva de propósito: a app online corre o código anterior contra esta mesma BD
-- até ao próximo deploy, e esse código lê `break_minutes`. Por isso a coluna fica e
-- passa a ser derivada (= lunch_end - lunch_start, ou 0 sem almoço), escrita pelo
-- `WorkScheduleDay`. Apagá-la é uma migração futura, depois do deploy.
--
-- Sem almoço (as duas null) = dia de um só período, p.ex. sábado de manhã.

alter table attendance.work_schedule_day
    add column if not exists lunch_start time,
    add column if not exists lunch_end   time;

alter table attendance.work_schedule_day
    add constraint ck_work_schedule_day_lunch check (
        (lunch_start is null and lunch_end is null)
        or (lunch_start is not null and lunch_end is not null
            and start_time < lunch_start and lunch_start < lunch_end and lunch_end < end_time)
    );

-- O único horário existente era 07:00–16:00 com 60 min. O horário real da Vilatro é
-- 08:00–17:00 com almoço 12:00–13:00 (indicado pelo utilizador a 2026-11-20).
update attendance.work_schedule_day d
   set start_time = time '08:00', end_time = time '17:00',
       lunch_start = time '12:00', lunch_end = time '13:00', break_minutes = 60
  from attendance.work_schedule s
 where s.id = d.schedule_id
   and s.name = 'Horário Normal'
   and d.start_time = time '07:00' and d.end_time = time '16:00' and d.break_minutes = 60;

-- Outros dias com pausa e sem janela (não há nenhum hoje): almoço a meio do dia,
-- para o cálculo novo não deixar de descontar o que o antigo descontava.
update attendance.work_schedule_day
   set lunch_start = start_time + ((end_time - start_time) - make_interval(mins => break_minutes)) / 2,
       lunch_end   = start_time + ((end_time - start_time) - make_interval(mins => break_minutes)) / 2
                     + make_interval(mins => break_minutes)
 where lunch_start is null and break_minutes > 0;
