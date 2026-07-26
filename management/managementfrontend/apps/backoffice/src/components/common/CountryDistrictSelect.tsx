import { useMemo } from "react";
import { Select, Space } from "antd";
import { useTranslation } from "react-i18next";

const { Option } = Select;

/** Países suportados - AGORA COM NOMES */
export type CountryName = "Portugal" | "Espanha" | "França";

/** Valor do componente */
export interface CountryDistrictValue {
  country?: CountryName | null;
  district?: string | null;
}

interface Props {
  value?: CountryDistrictValue;
  onChange?: (v: CountryDistrictValue) => void;
  disabled?: boolean;
  countryLabel?: string;
  districtLabel?: string;
  size?: "small" | "middle" | "large";
  placeholders?: { country?: string; district?: string };
  requireCountryFirst?: boolean;
}

/* ---- Dados ---- */

/* Portugal — 18 distritos (continente) */
const PT_DISTRICTS = [
  "Aveiro","Beja","Braga","Bragança","Castelo Branco","Coimbra","Évora","Faro","Guarda",
  "Leiria","Lisboa","Portalegre","Porto","Santarém","Setúbal","Viana do Castelo","Vila Real","Viseu",
];

/* Espanha — 52 províncias (inclui Ceuta/Melilla) */
const ES_PROVINCES = [
  "A Coruña","Álava","Albacete","Alicante","Almería","Asturias","Ávila","Badajoz","Illes Balears",
  "Barcelona","Bizkaia","Burgos","Cáceres","Cádiz","Cantabria","Castellón","Ciudad Real","Córdoba",
  "Cuenca","Gipuzkoa","Girona","Granada","Guadalajara","Huelva","Huesca","Jaén","La Rioja",
  "Las Palmas","León","Lleida","Lugo","Madrid","Málaga","Murcia","Navarra","Ourense","Palencia",
  "Pontevedra","Salamanca","Santa Cruz de Tenerife","Segovia","Sevilla","Soria","Tarragona","Teruel",
  "Toledo","Valencia","Valladolid","Zamora","Zaragoza","Ceuta","Melilla",
];

/* França — 13 regiões (metropolitanas) */
const FR_REGIONS = [
  "Auvergne-Rhône-Alpes","Bourgogne-Franche-Comté","Bretagne","Centre-Val de Loire","Corse",
  "Grand Est","Hauts-de-France","Île-de-France","Normandie","Nouvelle-Aquitaine","Occitanie",
  "Pays de la Loire","Provence-Alpes-Côte d'Azur",
];

/* Mapa de "distritos" por país */
const DISTRICTS_BY_COUNTRY: Record<CountryName, string[]> = {
  "Portugal": PT_DISTRICTS,
  "Espanha": ES_PROVINCES,
  "França": FR_REGIONS,
};

export default function CountryDistrictSelect({
  value,
  onChange,
  disabled,
  countryLabel: _countryLabel,  // eslint-disable-line @typescript-eslint/no-unused-vars
  districtLabel: _districtLabel,  // eslint-disable-line @typescript-eslint/no-unused-vars
  size = "middle",
  placeholders,
  requireCountryFirst = true,
}: Props) {
  const { t } = useTranslation();
  const country = value?.country ?? null;
  const district = value?.district ?? null;

  const districtOptions = useMemo(() => {
    if (!country) return [];
    return DISTRICTS_BY_COUNTRY[country as CountryName] ?? [];
  }, [country]);

  function handleCountryChange(nextCountry?: CountryName) {
    onChange?.({
      country: nextCountry ?? null,
      district: null,
    });
  }

  function handleDistrictChange(nextDistrict?: string) {
    onChange?.({
      country,
      district: nextDistrict ?? null,
    });
  }

  return (
    <Space.Compact block size={size}>
      {/* País */}
      <Select
        value={country ?? undefined}
        onChange={(v) => handleCountryChange(v as CountryName)}
        allowClear
        showSearch
        optionFilterProp="children"
        placeholder={placeholders?.country ?? t('countrySelect.countryPlaceholder')}
        disabled={disabled}
      >
        {(["Portugal", "Espanha", "França"] as CountryName[]).map((countryName) => (
          <Option key={countryName} value={countryName}>
            {countryName}
          </Option>
        ))}
      </Select>

      {/* Distrito/Província/Região */}
      <Select
        value={district ?? undefined}
        onChange={(v) => handleDistrictChange(v as string)}
        allowClear
        showSearch
        optionFilterProp="children"
        placeholder={
          placeholders?.district ??
          (country
            ? `${t('countrySelect.districtPlaceholder').replace('distrito', labelByCountry(country, t))}`
            : t('countrySelect.selectCountryFirst'))
        }
        disabled={disabled || (requireCountryFirst && !country)}
        notFoundContent={
          country ? t('countrySelect.noOptions') : t('countrySelect.chooseCountryFirst')
        }
      >
        {districtOptions.map((d) => (
          <Option key={d} value={d}>
            {d}
          </Option>
        ))}
      </Select>
    </Space.Compact>
  );
}

/* label contextual para o segundo select */
function labelByCountry(country?: CountryName | null, t?: (key: string) => string): string {
  const translate = t ?? ((k: string) => k);
  switch (country) {
    case "Portugal": return translate('countrySelect.labelDistrict');
    case "Espanha": return translate('countrySelect.labelProvince');
    case "França": return translate('countrySelect.labelRegion');
    default: return translate('countrySelect.labelDefault');
  }
}
