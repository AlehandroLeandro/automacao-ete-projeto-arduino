-- Troca os identificadores das estações para nomes genéricos.
-- Sem isso, a semeadura inicial (que procura pelo slug) criaria estações
-- duplicadas em bancos que já existem.
UPDATE stations SET slug = 'ete-empresa-1' WHERE slug = 'ete-novo-horizonte';
UPDATE stations SET slug = 'ete-empresa-2' WHERE slug = 'ete-boa-vista';

-- Só renomeia quem ainda usa o nome padrão antigo; nomes personalizados ficam.
UPDATE stations SET name = 'Empresa 1' WHERE name = 'ETE Novo Horizonte';
UPDATE stations SET name = 'Empresa 2' WHERE name = 'ETE Boa Vista';
