-- A contact phone number for each lead (from the Google Maps listing, or read from the company's website).
ALTER TABLE leads ADD COLUMN phone VARCHAR(60) NULL;
